# Postmortem: Duplikate saker og notifikasjoner etter Kafka-outage

**Dato:** 2026-05-18  
**Hendelsesperiode:** 2026-05-15 20:50 – 2026-05-17  
**Alvorlighetsgrad:** Moderat  
**Status:** Løst  

## Sammendrag

Under en planlagt oppgradering av Kafka-clusteret over helgen 15.–17. mai 2026 oppsto driftsforstyrrelser som førte til at vår interne Kafka-produsent hang i over 30 sekunder mot Kafka. Mens produsenten hang, timet et par API-kall ut hos klienten (produsenten av notifikasjoner), som deretter forsøkte å sende de samme forespørslene på nytt. Vi mottok og kvitterte feilaktig for disse duplikate forespørslene, noe som resulterte i duplikate saker og oppgaver i systemet.

## Tidslinje

| Tid (CET)         | Hendelse                                                                                                  |
|-------------------|-----------------------------------------------------------------------------------------------------------|
| 2026-05-15 ~20:50 | Kafka-cluster oppgradering starter, driftsforstyrrelser oppstår                                           |
| 2026-05-15 20:50:22 | Vår Kafka-produsent henger mot Kafka i >30 sekunder. API-kall timer ut hos produsenter som forsøker retry |
| 2026-05-15 20:50:22 | Duplikate hendelser mottas og kvitteres for (bl.a. hendelseId `20d2bbb3-a6b8-4c24-9753-d0e41b7a1d38`)     |
| 2026-05-17        | Kafka-oppgraderingen fullføres, drift normaliseres                                                        |
| 2026-05-18 08:00  | team-fager starter dagen og ser over alerts. oppdager consumer lagg og restarter appene                   |
| 2026-05-18 09:00  | oppdager stick partisjoner pga fkey constraint som har oppstått pga race condition / duplikate hendelser  |
| 2026-05-18 12:00  | Tiltak 1 deployet: Duplikate hendelser lagt til i skip-liste                                              |
| 2026-05-18 12:00  | Tiltak 2 deployet: `MAX_BLOCK_MS_CONFIG` redusert fra 30s til 5s                                          |
| 2026-05-18 14:00  | produsent helsearbeidsgiver informeres om hendelsen og konsekvensene                                      |

## Konsekvens

To saker ble berørt med duplikate notifikasjoner:

1. **eksternId `08aa9de0-b1f5-466b-a12d-594a8b26f76d`** (im-notifikasjon / Inntektsmelding sykepenger, virksomhet 974542277):
   - 3 duplikate saker
   - 3 duplikate oppgaver (én av oppgavene er utført korrekt)

2. **eksternId `fdd795c4-9b8f-4361-a721-77dc23a5ba89`**:
   - 1 duplikat oppgave

Duplikatene er **ikke ryddet opp**. Arbeidsgiver kan oppleve å se samme informasjon flere ganger ved innlogging.

Konsumentsiden (produsent-modellen) oppdaget duplikatene via foreign key constraint violations i databasen:
```
PSQLException: ERROR: insert or update on table "mottaker_altinn_ressurs" 
violates foreign key constraint "mottaker_altinn_ressurs_notifikasjon_id_fkey"
```
Disse feilene ble kontinuerlig retryet (observert opp til attempt 32 med backoff på ~5 min).

## Rotårsak

1. **`MAX_BLOCK_MS_CONFIG` var satt til 30 sekunder.** Da Kafka-clusteret var utilgjengelig, blokkerte vår Kafka-produsent i opptil 30 sekunder før den feilet. Dette er lenger enn timeout-verdien til flere av våre klienter (produsentene som kaller API-et).

2. **Manglende idempotens-håndtering ved mottak.** Når klienten timet ut og sendte forespørselen på nytt, mottok vi begge forespørslene og behandlet dem som separate hendelser. Vi manglet tilstrekkelig dedup-logikk for å fange opp at det var identiske forespørsler.

## Tiltak

### Gjennomført

| # | Tiltak | PR/Commit | Status |
|---|--------|-----------|--------|
| 1 | Redusert `MAX_BLOCK_MS_CONFIG` fra 30s til 5s for å feile raskere mot Kafka | `fe84b919` | Deployet |
| 2 | Lagt til duplikate hendelseId-er i skip-liste for å stoppe retry-loop i konsumenter | `fa47f150` | Deployet |
| 3 | Justert retry-logikk for å redusere støy i alerts-kanalen | `0a643518` | Deployet |

### Ikke gjennomført

- **Opprydding av duplikate saker/oppgaver:** Duplikatene er foreløpig ikke fjernet fra databasen. Arbeidsgiver kan se duplikat informasjon.

## Berørte hendelse-IDer (skip-listet)

- `20d2bbb3-a6b8-4c24-9753-d0e41b7a1d38`
- `93f96dbf-7af6-40d7-b46d-1de67a50831a`
- `0a2b4ee0-84cf-4d2a-9fe3-d2227d08b194`

## Lærdom

- En `MAX_BLOCK_MS_CONFIG` på 30 sekunder er for høy når klienter har kortere timeout. Produsenten bør feile innenfor klientens timeout-vindu slik at klienten ikke sender retry mens vi fortsatt holder på å prosessere den opprinnelige forespørselen.
- Ved Kafka-utilgjengelighet bør vi feile raskt (fail fast) fremfor å henge, slik at klienten får et tydelig feilsvar og kan ta stilling til retry selv.
