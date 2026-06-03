# Postmortem: Duplikate notifikasjoner etter Kafka-outage (gjentakelse)

**Dato:** 2026-06-01
**Hendelsesperiode:** 2026-06-01 ~17:18 – ~20:02 (CEST)
**Alvorlighetsgrad:** Moderat
**Status:** Stoppet (skip-liste), permanent tiltak under design
**Relatert:** [postmortem 2026-05-18](duplikater_etter_kafka_outage_2026-05-18.md), [spesifikasjon: idempotent oppretting](../specifications/idempotent_oppretting_notifikasjon.md)

## Sammendrag

Under en rebalansering av Kafka-clusteret hang vår Kafka-produsent mot Kafka, og `nyOppgave`-kall timet ut. Klientene sendte de samme forespørslene på nytt. Fordi `hendelseId`/`notifikasjonId` genereres tilfeldig per kall og idempotens-sjekken (`hentNotifikasjon`) var blind i tidsvinduet før den opprinnelige hendelsen ble synlig, mottok og kvitterte vi for retry-ene som *nye* hendelser. Resultatet ble duplikate oppgaver — og to partisjoner ble stående fast (poison pill) i konsumenten `produsent-model-builder` på grunn av foreign key-violations.

Dette er en **gjentakelse av samme rotårsak som 18. mai 2026**. Rotårsak #2 fra den gangen (manglende idempotens-håndtering) ble aldri lukket — kun timeout-verdien (`MAX_BLOCK_MS`) ble justert, noe som paradoksalt nok øker retry-trykket.

I tillegg avdekket vi at appene **ikke gikk unhealthy og restartet** under hendelsen, fordi de dominerende feilmodusene (poison pill, commit-feil, og den synkrone throwen fra `send()`) ikke setter `Health.subsystemAlive[KAFKA] = false`.

## Tidslinje

| Tid (CEST)   | Hendelse                                                                                                       |
|--------------|----------------------------------------------------------------------------------------------------------------|
| ~17:18       | Kafka-cluster rebalanserer; driftsforstyrrelser begynner (logg starter 15:18:31Z)                              |
| 17:19:54     | Første FK-violation i `nyOppgave`-mutasjonen selv (inline-skriving, `path=[nyOppgave]`, `mottaker_digisyfo`) — duplikat opprettes under race |
| 17:21:48     | Første FK-violation i `produsent-model-builder` — poison pill starter                                          |
| 17:24:53     | `nyOppgave` timer ut mot Kafka: `Expiring 1 record(s) for fager.notifikasjon-19:120001 ms` (delivery timeout, 120s) |
| 17:42        | `produsent-model-builder` commit feiler: `The coordinator is not aware of this member`                          |
| 18:06–18:27  | `min-side-arbeidsgiver-sykmelding`, `autoslett-service`, `sendAktuellePåminnelser` feiler med timeout/commit-feil |
| 19:06–19:10  | `skedulert-harddelete-model-builder` commit feiler; `nyOppgave`: `Topic fager.notifikasjon not present in metadata after 5000 ms` (`MAX_BLOCK_MS`, **usynlig for helsesjekk**) |
| 19:37        | `nyOppgave` inline-skriving FK-violation igjen (duplikat #2)                                                    |
| ~20:02       | Siste logglinje; poison pill fortsatt aktiv (`attempt=14`, backoff ~5 min). Appene aldri gått unhealthy.        |

> Tidspunkter er konvertert fra logg (UTC) til CEST (UTC+2).

## Konsekvens

To `OppgaveOpprettet`-duplikater ble stående som poison pill i `produsent-model-builder` og ble retryet kontinuerlig (observert opp til `attempt=14`, backoff ~5 min):

1. **`f438ddc3-8ec6-4556-9baf-a79486a70830`** — produsent `k9-inntektsmelding-notifikasjon` (`prod-gcp:k9saksbehandling:k9-inntektsmelding`), partisjon 11. FK-violation på `mottaker_altinn_ressurs` (47 forsøk i loggvinduet).
2. **`11684dd5-f073-4aec-8b53-b115f6b8ec30`** — produsent `esyfovarsel` (`prod-gcp:team-esyfo:esyfovarsel`), partisjon 1. FK-violation på `mottaker_digisyfo` (18 forsøk i loggvinduet).

```
PSQLException: ERROR: insert or update on table "mottaker_altinn_ressurs"
violates foreign key constraint "mottaker_altinn_ressurs_notifikasjon_id_fkey"
  Detail: Key (notifikasjon_id)=(f438ddc3-...-a79486a70830) is not present in table "notifikasjon".
```

Følgende consumer-grupper logget `coordinator is not aware of this member` under rebalanseringen: `produsent-model-builder` (×7), `skedulert-paaminnelse-2`, `skedulert-harddelete-model-builder-1`, `min-side-arbeidsgiver-sykmelding-1`, `replay-validator`, `kafka-bq-v1`, `ekstern-varsling-status-exporter`.

Duplikatene er **ikke ryddet opp** i databasen. Arbeidsgiver kan oppleve å se samme oppgave flere ganger.

## Etterspill — consumer krasjer dagen derpå (2026-06-02)

Morgenen etter, *etter* at clusteret var friskt, fanget loggene den fulle feilkjeden på en annen gruppe på **samme `CoroutineKafkaConsumer`-kode** — `reaper-model-builder` (`notifikasjon-kafka-reaper`), på *vanlige* records (`EksterntVarselVellykket`, `BrukerKlikket`, **ingen poison pill**). Konsumenten var ikke bare «stuck» — den **krasjet**:

```
08:20:22  TimeoutException: Timeout of 60000ms expired before successfully committing offsets   ← commitSync blokkerte 60s
08:21:00  CommitFailedException: ... the time between subsequent calls to poll() was longer than max.poll.interval.ms
08:21:05  IllegalStateException: No current assignment for partition fager.notifikasjon-1
              at CoroutineKafkaConsumer.kt:123  (consumer.resume(...))   StandaloneCoroutine{Cancelling}  ← coroutine dør
```

Dette viser at problemet er **selvforsterkende og generisk** for alle konsumenter som bruker `CoroutineKafkaConsumer`: den ubundne `commitSync` blokkerer i 60s (`default.api.timeout.ms`), poll-loopen overskrider `max.poll.interval.ms` og medlemmet kastes ut av gruppen, og deretter krasjer `consumer.resume(resumeQueue.pollAll())` på en partisjon som rebalanseringen allerede har revokert. Coroutinen dør stille mens poden fortsatt rapporterer «alive». Se [spesifikasjon: robust rebalansering](../specifications/robust_consumer_rebalance.md).

## Rotårsak

1. **Manglende idempotens som overlever et produce-timeout (uendret siden 18. mai).** `nyOppgave`/`nyBeskjed` genererer `id = UUID.randomUUID()` (`MutationNyOppgave.kt:107`) og sjekker `hentNotifikasjon(eksternId, merkelapp)` *før* produsering. Den sjekken er blind i vinduet mellom «attempt-1 produserer H1» og «H1 er synlig» — fordi inline-skrivingen i `HendelseDispatcher` kun skjer *etter* vellykket produsering, og `produsent-model-builder` ligger bak under outage. En retry leser `null`, lager ny tilfeldig id, og produserer et duplikat med *annen* aggregateId for samme `(merkelapp, eksternId)`.

2. **`unikt_koordinat` + `on conflict do nothing` gir poison pill.** `notifikasjon`-tabellen har `constraint unikt_koordinat unique (merkelapp, ekstern_id)` (`V1__init.sql:14`). Når duplikatet (H2) konsumeres, svelger `insert into notifikasjon ... on conflict do nothing` koordinat-konflikten, så raden for H2 opprettes aldri, og den påfølgende ubetingede `storeMottaker(H2, ...)` feiler med FK-violation. Samme feil treffer også mutasjonens egen inline-skriving (`path=[nyOppgave]`) når H1 materialiseres i et race mellom `hentNotifikasjon` og inline-insert.

3. **Appene gikk ikke unhealthy.** `Health.subsystemAlive[KAFKA]` settes kun til `false` tre steder: `consumer.poll()` som kaster (`CoroutineKafkaConsumer.kt:129`), produsent-callback-feil (`HendelseProdusentKafkaImpl.kt:61`), og partition-loop-krasj. De dominerende feilmodusene unngår alle tre:
   - **Poison pill og commit-feil** (`coordinator is not aware of this member`) fanges av den generiske per-record `catch` (`CoroutineKafkaConsumer.kt:176`) og retryes for alltid uten å røre `Health`.
   - **Synkron throw fra `send()`** (`Topic not present in metadata after 5000 ms`, fra `MAX_BLOCK_MS=5000`) går utenom callbacken og setter aldri `KAFKA=false`. Kun den asynkrone delivery-timeouten gjør det.
   - **Krasj i consumer-coroutinen** (`IllegalStateException` fra `consumer.resume`/`seek`/`pause` på revokert partisjon, jf. etterspillet 06-02) unwinder ut av `forEach` og dreper coroutinen — men `forEach` har ingen `Health`-håndtering der, så poden forblir «alive» med en død consumer.

4. **Den ubundne `commitSync` er triggeren bak commit-loopen.** `commitSync` (`CoroutineKafkaConsumer.kt:171`) kalles uten timeout og blokkerer opptil `default.api.timeout.ms` = 60s. Per record (opptil `max.poll.records=10`) blåser dette poll-intervallet forbi `max.poll.interval.ms`, slik at medlemmet kastes ut — selvforsterkende, uavhengig av om clusteret er friskt.

5. **18. mai-tiltaket forsterket trykket.** `MAX_BLOCK_MS` ble redusert 30s → 5s for å «feile raskt». Det gjør at klienter får feil og retryer *raskere*, mens idempotens-hullet fortsatt er åpent — altså flere duplikater, ikke færre.

## Tiltak

### Gjennomført

| # | Tiltak | Status |
|---|--------|--------|
| 1 | Duplikate hendelseId-er lagt til i skip-liste (`brokenHendelseId` i `HendelsesstrømKafkaImpl`) for å stoppe retry-loopen | Deployet (nå overflødig, se #2 — oppføringene kan fjernes) |
| 2 | **0-rows-skip i produsent-modellen.** Fang returverdien fra `insert into notifikasjon ... on conflict do nothing`; ved 0 rader logges error (notifikasjonId/merkelapp/eksternId) og `return@transaction`, slik at mottaker- og varsel-insertene hoppes over. Gjort identisk i `oppdaterModellEtterBeskjedOpprettet`, `oppdaterModellEtterOppgaveOpprettet` og `oppdaterModellEtterKalenderavtaleOpprettet`. Fjerner poison pillen automatisk (ingen skip-liste/redeploy), beholder invarianten «én notifikasjon per koordinat» (ingen synlige duplikater), og er deterministisk fordi duplikatene havner på samme partisjon (samme `virksomhetsnummer`) → first-produced vinner. | Deployet (commit `d1a8609c`) |

**Restrisiko ved #2:** fiksen dropper duplikatets `notifikasjon`-rad, så etterfølgende hendelser på den droppede id-en som *insert-er* child-rader med FK til `notifikasjon` kan fortsatt FK-feile (ny poison pill). Trygge (no-op UPDATE/delete på manglende rad): `OppgaveUtført`, `OppgaveUtgått`, soft/hard delete, `EksterntVarsel*`. Risiko (INSERT child FK): `FristUtsatt` → `paaminnelse_eksternt_varsel`, `OppgavePåminnelseEndret`/`KalenderavtaleOppdatert` → `notifikasjon_oppdatering`. Disse er ikke guardet av #2. «Komplett» variant ville guardet child-insertene i disse handlerne også; vurder dersom restrisikoen materialiseres.

**Vurderte alternativer (ikke valgt):** manuell skip-liste (#1 — blunt, krever redeploy, blokkerer partisjonen, foreldreløse follow-ups) og å relaksere `unikt_koordinat` (ingen foreldreløse follow-ups, men institusjonaliserer synlige duplikater, svekker idempotens-backstop, ikke-deterministisk `hentNotifikasjon`, og enveisdør). 0-rows-skip ble valgt fordi den beholder invarianten, er automatisk og deterministisk, og ikke krever schema-endring.

### Under design

| # | Tiltak | Spesifikasjon | Beskrivelse |
|---|--------|---------------|-------------|
| 3 | Transaksjonell outbox + idempotens for alle mutasjoner | [idempotent oppretting](../specifications/idempotent_oppretting_notifikasjon.md) | Alle event-produserende mutasjoner går via `HendelseDispatcher` til en outbox-tabell i én DB-transaksjon (claim idempotensnøkkel → enqueue → oppdater read-model); en relay drenerer outboxen til Kafka. Fjerner Kafka fra request-pathen (ingen produce-timeout/hang, ingen klient-retries → ingen duplikater) og gjør idempotens-sjekken pålitelig (ingen blind vindu). Hver mutasjon får en idempotensnøkkel (koordinat for creates, produsent-nøkkel for oppdateringer, avledet for finalizing/delete). `hendelseId`-generering endres ikke. Dette er årsaksfiksen; #2 er en symptomfiks (duplikatene produseres fortsatt). |
| 4 | Helsesjekk på synkron `send()`-throw | — | Wrap `send(...)` i `suspendingSend` slik at `MAX_BLOCK_MS`-timeout også setter `KAFKA=false`, symmetrisk med async-pathen. NB: kan gi restart-storm under rebalansering — vurder terskel. |
| 5 | Robust rebalansering / commit-håndtering i consumer | [robust rebalansering](../specifications/robust_consumer_rebalance.md) | Bind `commitSync` med timeout (eller commit per batch), skill data- fra control-plane (abort batch + rejoin ved commit-feil), `ConsumerRebalanceListener` på normal-path, og guard `resume`/`seek`/`pause` mot gjeldende assignment slik at consumer-coroutinen ikke krasjer på revokert partisjon. Poison-pill-retry beholdes uendret. |

### Ikke gjennomført

- **Opprydding av duplikater** fra denne og tidligere hendelser (fortsatt utestående siden mai).

## Berørte hendelse-IDer (skip-listet)

- `f438ddc3-8ec6-4556-9baf-a79486a70830` (k9-inntektsmelding-notifikasjon, partisjon 11)
- `11684dd5-f073-4aec-8b53-b115f6b8ec30` (esyfovarsel, partisjon 1)

## Lærdom

- **Symptomfiks holder ikke.** Vi justerte timeout i mai uten å lukke idempotens-hullet (rotårsak #2). Samme hendelse gjentok seg. Idempotens må adresseres for å bryte mønsteret.
- **«Feil raskt» uten idempotens øker duplikatrisikoen.** Lavere `MAX_BLOCK_MS` gir flere klient-retries; det hjelper bare hvis retry er trygt.
- **Stille feil er farligst.** De feilmodusene som faktisk rammet oss er nettopp de som ikke trigger helsesjekk eller alert. Poison-pill-retry-for-alltid er ønsket oppførsel, men vi trenger synlighet (metrikk/alert på fastlåst partisjon / stigende `attempt`), og den synkrone `send()`-throwen skal være synlig.
- **Et produce-timeout betyr ikke at produseringen feilet.** Den opprinnelige posten kan fortsatt lande på topic-en senere; idempotens-løsningen må anta tvetydig utfall.
- **En blokkerende commit kan kaste ut sin egen consumer.** `commitSync` uten timeout (60s) × per record kan i seg selv overskride `max.poll.interval.ms` og utløse eviction — feilen er da selvforsterkende og forsvinner ikke når clusteret blir friskt. Commit må være tidsbegrenset.
- **En død consumer-coroutine er usynlig.** Et ufanget `IllegalStateException` på en revokert partisjon dreper consumer-loopen mens poden forblir «alive». Dette er trolig det ene tilfellet som *bør* slå ut på helsesjekk, siden poden ikke kan komme seg selv.
