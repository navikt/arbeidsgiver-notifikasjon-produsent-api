---
layout: page
title: API-dokumentasjon
permalink: /api/
---

Interaktiv demo av API [er tilgjengelig på labs.nais.io](https://notifikasjon-fake-produsent-api.labs.nais.io/).


# Autentisering
Tjenesten deres må autentisere seg med Azure AD, type server–server, som [beskrevet i nais-dokumentasjonen](https://doc.nais.io/security/auth/azure-ad/).

# Endepunkter for miljøer 

miljø | url
-----|------
mock | `https://notifikasjon-fake-produsent-api.labs.nais.io/api/graphql`
dev | fra gcp: `https://ag-notifikasjon-produsent-api.dev.nav.no/api/graphql`, <br/> fra fss: `https://ag-notifikasjon-produsent-api.dev.intern.nav.no/api/graphql`
prod | `https://ag-notifikasjon-produsent-api.intern.nav.no/api/graphql`


# GraphQL over HTTP

Vi implementerer GraphQL over HTTP (kun POST, ikke GET) og JSON, basert på de offisielle anbefalingene: [https://graphql.org/learn/serving-over-http/](https://graphql.org/learn/serving-over-http/).

> ⚠️ GraphQL returnerer alltid en ["well-formed" HTTP 200 OK](https://spec.graphql.org/October2021/#sec-Response).
Dersom det er en eller flere valideringsfeil eller noe annet ugyldig vil det returneres informasjon om dette i [errors feltet](https://spec.graphql.org/October2021/#sec-Errors) i response body fra server.

Vi anbefaler at dere angir correlationId i kall dere gjør mot APIet. Dette vil lette arbeidet med feilsøking og oppfølging.
Vi plukker verdien ut fra en av følgende headere:
- `X-Request-ID`
- `X-Correlation-ID`
- `call-id`
- `callId`
- `call_id`

Med dette angitt kan dere søke i kibana etter `x_correlation_id`.

## GraphQL Schema Types

<!-- START graphql-markdown -->

<details>
  <summary><strong>Table of Contents</strong></summary>

  * [Query](#query)
  * [Mutation](#mutation)
  * [Objects](#objects)
    * [AltinnMottaker](#altinnmottaker)
    * [AltinnReporteeMottaker](#altinnreporteemottaker)
    * [AltinnRolleMottaker](#altinnrollemottaker)
    * [Beskjed](#beskjed)
    * [BeskjedData](#beskjeddata)
    * [DuplikatEksternIdOgMerkelapp](#duplikateksternidogmerkelapp)
    * [DuplikatGrupperingsid](#duplikatgrupperingsid)
    * [EksterntVarsel](#eksterntvarsel)
    * [HardDeleteNotifikasjonVellykket](#harddeletenotifikasjonvellykket)
    * [HardDeleteSakVellykket](#harddeletesakvellykket)
    * [Konflikt](#konflikt)
    * [Metadata](#metadata)
    * [NaermesteLederMottaker](#naermesteledermottaker)
    * [NotifikasjonConnection](#notifikasjonconnection)
    * [NotifikasjonEdge](#notifikasjonedge)
    * [NotifikasjonFinnesIkke](#notifikasjonfinnesikke)
    * [NyBeskjedVellykket](#nybeskjedvellykket)
    * [NyEksterntVarselResultat](#nyeksterntvarselresultat)
    * [NyOppgaveVellykket](#nyoppgavevellykket)
    * [NySakVellykket](#nysakvellykket)
    * [NyStatusSakVellykket](#nystatussakvellykket)
    * [Oppgave](#oppgave)
    * [OppgaveData](#oppgavedata)
    * [OppgaveUtfoertVellykket](#oppgaveutfoertvellykket)
    * [OppgaveUtgaattVellykket](#oppgaveutgaattvellykket)
    * [OppgavenErAlleredeUtfoert](#oppgaveneralleredeutfoert)
    * [PaaminnelseResultat](#paaminnelseresultat)
    * [PageInfo](#pageinfo)
    * [SakFinnesIkke](#sakfinnesikke)
    * [SoftDeleteNotifikasjonVellykket](#softdeletenotifikasjonvellykket)
    * [SoftDeleteSakVellykket](#softdeletesakvellykket)
    * [StatusOppdatering](#statusoppdatering)
    * [UgyldigMerkelapp](#ugyldigmerkelapp)
    * [UgyldigMottaker](#ugyldigmottaker)
    * [UgyldigPaaminnelseTidspunkt](#ugyldigpaaminnelsetidspunkt)
    * [UkjentProdusent](#ukjentprodusent)
    * [UkjentRolle](#ukjentrolle)
  * [Inputs](#inputs)
    * [AltinnMottakerInput](#altinnmottakerinput)
    * [AltinnReporteeMottakerInput](#altinnreporteemottakerinput)
    * [AltinnRolleMottakerInput](#altinnrollemottakerinput)
    * [EksterntVarselEpostInput](#eksterntvarselepostinput)
    * [EksterntVarselInput](#eksterntvarselinput)
    * [EksterntVarselSmsInput](#eksterntvarselsmsinput)
    * [EpostKontaktInfoInput](#epostkontaktinfoinput)
    * [EpostMottakerInput](#epostmottakerinput)
    * [FutureTemporalInput](#futuretemporalinput)
    * [HardDeleteUpdateInput](#harddeleteupdateinput)
    * [MetadataInput](#metadatainput)
    * [MottakerInput](#mottakerinput)
    * [NaermesteLederMottakerInput](#naermesteledermottakerinput)
    * [NotifikasjonInput](#notifikasjoninput)
    * [NyBeskjedInput](#nybeskjedinput)
    * [NyOppgaveInput](#nyoppgaveinput)
    * [PaaminnelseEksterntVarselEpostInput](#paaminnelseeksterntvarselepostinput)
    * [PaaminnelseEksterntVarselInput](#paaminnelseeksterntvarselinput)
    * [PaaminnelseEksterntVarselSmsInput](#paaminnelseeksterntvarselsmsinput)
    * [PaaminnelseInput](#paaminnelseinput)
    * [PaaminnelseTidspunktInput](#paaminnelsetidspunktinput)
    * [SendetidspunktInput](#sendetidspunktinput)
    * [SmsKontaktInfoInput](#smskontaktinfoinput)
    * [SmsMottakerInput](#smsmottakerinput)
  * [Enums](#enums)
    * [EksterntVarselStatus](#eksterntvarselstatus)
    * [NyTidStrategi](#nytidstrategi)
    * [OppgaveTilstand](#oppgavetilstand)
    * [SaksStatus](#saksstatus)
    * [Sendevindu](#sendevindu)
  * [Scalars](#scalars)
    * [Boolean](#boolean)
    * [ID](#id)
    * [ISO8601Date](#iso8601date)
    * [ISO8601DateTime](#iso8601datetime)
    * [ISO8601Duration](#iso8601duration)
    * [ISO8601LocalDateTime](#iso8601localdatetime)
    * [Int](#int)
    * [String](#string)
  * [Interfaces](#interfaces)
    * [Error](#error)
  * [Unions](#unions)
    * [HardDeleteNotifikasjonResultat](#harddeletenotifikasjonresultat)
    * [HardDeleteSakResultat](#harddeletesakresultat)
    * [MineNotifikasjonerResultat](#minenotifikasjonerresultat)
    * [Mottaker](#mottaker)
    * [Notifikasjon](#notifikasjon)
    * [NyBeskjedResultat](#nybeskjedresultat)
    * [NyOppgaveResultat](#nyoppgaveresultat)
    * [NySakResultat](#nysakresultat)
    * [NyStatusSakResultat](#nystatussakresultat)
    * [OppgaveUtfoertResultat](#oppgaveutfoertresultat)
    * [OppgaveUtgaattResultat](#oppgaveutgaattresultat)
    * [SoftDeleteNotifikasjonResultat](#softdeletenotifikasjonresultat)
    * [SoftDeleteSakResultat](#softdeletesakresultat)

</details>

### Query
Dette er roten som alle forespørsler starter fra.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>whoami</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Egnet for feilsøking. Forteller hvem du er autentisert som.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>mineNotifikasjoner</strong></td>
<td valign="top"><a href="#minenotifikasjonerresultat">MineNotifikasjonerResultat</a>!</td>
<td>

Vi bruker det Connections-patternet for paginering. Se
[Connection-standaren](https://relay.dev/graphql/connections.htm) for mer
informasjon.

Dere må gjenta paremetere når dere blar gjennom alle notifikasjonen.

Hvis verken `merkelapp` eller `merkelapper` er gitt, vil notifikasjoner
med alle dine merkelapper være med.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">first</td>
<td valign="top"><a href="#int">Int</a></td>
<td>

antall notifikasjoner du ønsker å hente

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">after</td>
<td valign="top"><a href="#string">String</a></td>
<td>

Cursor til notifikasjonen du henter fra. Cursor får du fra [NotifikasjonEdge](#notifikasjonedge).

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a></td>
<td>

Filtrer på merkelapp. Kan ikke brukes sammen med `merkelapper`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapper</td>
<td valign="top">[<a href="#string">String</a>!]</td>
<td>

Filtrer på merkelapper. Kan ikke brukes sammen med `merkelapp`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">grupperingsid</td>
<td valign="top"><a href="#string">String</a></td>
<td></td>
</tr>
</tbody>
</table>

### Mutation
Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
å opprette nye ting.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>nySak</strong></td>
<td valign="top"><a href="#nysakresultat">NySakResultat</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">grupperingsid</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Grupperings-id-en knytter en sak og notifikasjoner sammen.
Den skal være unik for saker innenfor merkelappen.
Et naturlig valg av grupperingsid er f.eks. et saksnummer.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelapp som saken skal assossieres med.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">virksomhetsnummer</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Virksomhetsnummeret til virksomheten som saken omhandler.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">mottakere</td>
<td valign="top">[<a href="#mottakerinput">MottakerInput</a>!]!</td>
<td>

Hvem som skal få se saken.

NB. At en bruker har tilgang til en sak påvirker ikke om de har tilgang
til en notifikasjon. De tilgangsstyres hver for seg.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">tittel</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

En tittel på saken, som vises til brukeren.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">lenke</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Her oppgir dere en lenke som brukeren kan klikke på for å komme rett til saken.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">initiellStatus</td>
<td valign="top"><a href="#saksstatus">SaksStatus</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">tidspunkt</td>
<td valign="top"><a href="#iso8601datetime">ISO8601DateTime</a></td>
<td>

Når endringen skjedde. Det kan godt være i fortiden.
Dette feltet er frivillig. Hvis feltet ikke er oppgitt, bruker vi tidspunktet dere gjør
kallet på.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">overstyrStatustekstMed</td>
<td valign="top"><a href="#string">String</a></td>
<td>

Dette feltet er frivillig. Det lar deg overstyre hvilken tekst vi viser
til brukeren. Se `SaksStatus` for default tekster.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#futuretemporalinput">FutureTemporalInput</a></td>
<td>

Oppgi dersom dere ønsker at hard delete skal skeduleres. Vi
tolker relative datoer basert på `opprettetTidspunkt` (eller
når vi mottok kallet hvis dere ikke har oppgitt `opprettetTidspunkt`).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>nyStatusSak</strong></td>
<td valign="top"><a href="#nystatussakresultat">NyStatusSakResultat</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">idempotencyKey</td>
<td valign="top"><a href="#string">String</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">id</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">nyStatus</td>
<td valign="top"><a href="#saksstatus">SaksStatus</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">tidspunkt</td>
<td valign="top"><a href="#iso8601datetime">ISO8601DateTime</a></td>
<td>

Når endringen skjedde. Det kan godt være i fortiden.
Dette feltet er frivillig. Hvis feltet ikke er oppgitt, bruker vi tidspunktet dere gjør
kallet på.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">overstyrStatustekstMed</td>
<td valign="top"><a href="#string">String</a></td>
<td>

Dette feltet er frivillig. Det lar deg overstyre hvilken tekst vi viser
til brukeren. Se `SaksStatus` for default tekster.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#harddeleteupdateinput">HardDeleteUpdateInput</a></td>
<td>

Se: [HardDeleteUpdateInput typen](#harddeleteupdateinput)

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">nyLenkeTilSak</td>
<td valign="top"><a href="#string">String</a></td>
<td>

Her kan dere endre lenken til saken. F.eks hvis saken har gått fra å være en
søknad til en kvittering, så kan det være dere har behov for å endre url som brukeren sendes til.
Dette feltet er frivillig; er det ikke oppgitt (eller er null), så forblir lenken knyttet til saken uendret.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>nyStatusSakByGrupperingsid</strong></td>
<td valign="top"><a href="#nystatussakresultat">NyStatusSakResultat</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">idempotencyKey</td>
<td valign="top"><a href="#string">String</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">grupperingsid</td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">nyStatus</td>
<td valign="top"><a href="#saksstatus">SaksStatus</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">tidspunkt</td>
<td valign="top"><a href="#iso8601datetime">ISO8601DateTime</a></td>
<td>

Når endringen skjedde. Det kan godt være i fortiden.
Dette feltet er frivillig. Hvis feltet ikke er oppgitt, bruker vi tidspunktet dere gjør
kallet på.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">overstyrStatustekstMed</td>
<td valign="top"><a href="#string">String</a></td>
<td>

Dette feltet er frivillig. Det lar deg overstyre hvilken tekst vi viser
til brukeren. Se `SaksStatus` for default tekster.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#harddeleteupdateinput">HardDeleteUpdateInput</a></td>
<td>

Se: [HardDeleteUpdateInput typen](#harddeleteupdateinput)

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">nyLenkeTilSak</td>
<td valign="top"><a href="#string">String</a></td>
<td>

Her kan dere endre lenken til saken. F.eks hvis saken har gått fra å være en
søknad til en kvittering, så kan det være dere har behov for å endre url som brukeren sendes til.
Dette feltet er frivillig; er det ikke oppgitt (eller er null), så forblir lenken knyttet til saken uendret.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>nyBeskjed</strong></td>
<td valign="top"><a href="#nybeskjedresultat">NyBeskjedResultat</a>!</td>
<td>

Opprett en ny beskjed.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">nyBeskjed</td>
<td valign="top"><a href="#nybeskjedinput">NyBeskjedInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>nyOppgave</strong></td>
<td valign="top"><a href="#nyoppgaveresultat">NyOppgaveResultat</a>!</td>
<td>

Opprett en ny oppgave.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">nyOppgave</td>
<td valign="top"><a href="#nyoppgaveinput">NyOppgaveInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>oppgaveUtfoert</strong></td>
<td valign="top"><a href="#oppgaveutfoertresultat">OppgaveUtfoertResultat</a>!</td>
<td>

Marker en oppgave (identifisert ved id) som utført.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">id</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en som oppgaven har. Den du fikk da du opprettet oppgaven med `nyOppgave`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#harddeleteupdateinput">HardDeleteUpdateInput</a></td>
<td>

Se: [HardDeleteUpdateInput typen](#harddeleteupdateinput)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>oppgaveUtfoertByEksternId</strong> ⚠️</td>
<td valign="top"><a href="#oppgaveutfoertresultat">OppgaveUtfoertResultat</a>!</td>
<td>

Marker en oppgave (identifisert ved ekstern id) som utført.

<p>⚠️ <strong>DEPRECATED</strong></p>
<blockquote>

Using the type ID for `eksternId` can lead to unexpected behaviour. Use oppgaveUtfoertByEksternId_V2 instead.

</blockquote>
</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelapp som oppgaven er registrert med.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">eksternId</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en som *dere ga oss* da dere opprettet oppgaven med `nyOppgave`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#harddeleteupdateinput">HardDeleteUpdateInput</a></td>
<td>

Se: [HardDeleteUpdateInput typen](#harddeleteupdateinput)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>oppgaveUtfoertByEksternId_V2</strong></td>
<td valign="top"><a href="#oppgaveutfoertresultat">OppgaveUtfoertResultat</a>!</td>
<td>

Marker en oppgave (identifisert ved ekstern id) som utført.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelapp som oppgaven er registrert med.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">eksternId</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

ID-en som *dere ga oss* da dere opprettet oppgaven med `nyOppgave`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#harddeleteupdateinput">HardDeleteUpdateInput</a></td>
<td>

Se: [HardDeleteUpdateInput typen](#harddeleteupdateinput)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>oppgaveUtgaatt</strong></td>
<td valign="top"><a href="#oppgaveutgaattresultat">OppgaveUtgaattResultat</a>!</td>
<td>

Marker en oppgave (identifisert ved id) som utgått.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">id</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en som oppgaven har. Den du fikk da du opprettet oppgaven med `nyOppgave`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#harddeleteupdateinput">HardDeleteUpdateInput</a></td>
<td>

Se: [HardDeleteUpdateInput typen](#harddeleteupdateinput)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>oppgaveUtgaattByEksternId</strong></td>
<td valign="top"><a href="#oppgaveutgaattresultat">OppgaveUtgaattResultat</a>!</td>
<td>

Marker en oppgave (identifisert ved ekstern id) som utgått.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelapp som oppgaven er registrert med.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">eksternId</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

ID-en som *dere ga oss* da dere opprettet oppgaven med `nyOppgave`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">hardDelete</td>
<td valign="top"><a href="#harddeleteupdateinput">HardDeleteUpdateInput</a></td>
<td>

Se: [HardDeleteUpdateInput typen](#harddeleteupdateinput)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>softDeleteNotifikasjon</strong></td>
<td valign="top"><a href="#softdeletenotifikasjonresultat">SoftDeleteNotifikasjonResultat</a>!</td>
<td>

Markerer en notifikasjon som slettet (soft delete).

Notifikasjonen vil forsvinne helt for mottakeren: de vil ikke kunne se den på
noen som helst måte — som om notifikasjonen aldri eksisterte.

For dere (produsenter), så kan dere fortsatt se notifikasjonen i listen over deres notifikasjoner.

Eventuelle eksterne varsler (SMS, e-post) knyttet til notifikasjonen vil bli fortsatt bli sendt.

Advarsel: det er ikke mulig å angre på denne operasjonen.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">id</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>softDeleteNotifikasjonByEksternId</strong> ⚠️</td>
<td valign="top"><a href="#softdeletenotifikasjonresultat">SoftDeleteNotifikasjonResultat</a>!</td>
<td>

Se dokumentasjon for `softDeleteNotifikasjon(id)`.

<p>⚠️ <strong>DEPRECATED</strong></p>
<blockquote>

Using the type ID for `eksternId` can lead to unexpected behaviour. Use softDeleteNotifikasjonByEksternId_V2 instead.

</blockquote>
</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelappen som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">eksternId</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>softDeleteNotifikasjonByEksternId_V2</strong></td>
<td valign="top"><a href="#softdeletenotifikasjonresultat">SoftDeleteNotifikasjonResultat</a>!</td>
<td>

Se dokumentasjon for `softDeleteNotifikasjon(id)`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelappen som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">eksternId</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

ID-en som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>hardDeleteNotifikasjon</strong></td>
<td valign="top"><a href="#harddeletenotifikasjonresultat">HardDeleteNotifikasjonResultat</a>!</td>
<td>

Sletter en notifikasjon og tilhørende data helt fra databasen og kafka.
Formålet er å støtte juridiske krav om sletting i henhold til personvern.

Eventuelle eksterne varsler (SMS, e-post) knyttet til notifikasjonen vil bli fortsatt bli sendt.

Advarsel: det er ikke mulig å angre på denne operasjonen. All data blir borte for godt.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">id</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>hardDeleteNotifikasjonByEksternId</strong> ⚠️</td>
<td valign="top"><a href="#harddeletenotifikasjonresultat">HardDeleteNotifikasjonResultat</a>!</td>
<td>

Se dokumentasjon for `hardDeleteNotifikasjon(id)`.

<p>⚠️ <strong>DEPRECATED</strong></p>
<blockquote>

Using the type ID for `eksternId` can lead to unexpected behaviour. Use hardDeleteNotifikasjonByEksternId_V2 instead.

</blockquote>
</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelappen som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">eksternId</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>hardDeleteNotifikasjonByEksternId_V2</strong></td>
<td valign="top"><a href="#harddeletenotifikasjonresultat">HardDeleteNotifikasjonResultat</a>!</td>
<td>

Se dokumentasjon for `hardDeleteNotifikasjon(id)`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelappen som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">eksternId</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

ID-en som dere ga oss da dere opprettet notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>softDeleteSak</strong></td>
<td valign="top"><a href="#softdeletesakresultat">SoftDeleteSakResultat</a>!</td>
<td>

Markerer en sak som slettet (soft delete).

Sak vil forsvinne helt for mottakeren: de vil ikke kunne se den på
noen som helst måte — som om saken aldri eksisterte.

Advarsel: det er ikke mulig å angre på denne operasjonen.
Advarsel: ingen notifikasjoner blir slettet, selv om de har samme grupperingsid.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">id</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>softDeleteSakByGrupperingsid</strong></td>
<td valign="top"><a href="#softdeletesakresultat">SoftDeleteSakResultat</a>!</td>
<td>

Se dokumentasjon for `softDeleteSak(id)`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelappen som dere ga oss da dere opprettet saken.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">grupperingsid</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

ID-en som dere ga oss da dere opprettet saken.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>hardDeleteSak</strong></td>
<td valign="top"><a href="#harddeletesakresultat">HardDeleteSakResultat</a>!</td>
<td>

Sletter en sak og tilhørende data helt fra databasen og kafka.
Formålet er å støtte juridiske krav om sletting i henhold til personvern.

Advarsel: det er ikke mulig å angre på denne operasjonen. All data blir borte for godt.
Advarsel: ingen notifikasjoner blir slettet (selv om de har samme grupperingsid).

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">id</td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>hardDeleteSakByGrupperingsid</strong></td>
<td valign="top"><a href="#harddeletesakresultat">HardDeleteSakResultat</a>!</td>
<td>

Se dokumentasjon for `hardDeleteSak(id)`.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">merkelapp</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelappen som dere ga oss da dere opprettet saken.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">grupperingsid</td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

ID-en som dere ga oss da dere opprettet saken.

</td>
</tr>
</tbody>
</table>

### Objects

#### AltinnMottaker

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>serviceCode</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>serviceEdition</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>virksomhetsnummer</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### AltinnReporteeMottaker

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>fnr</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>virksomhetsnummer</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### AltinnRolleMottaker

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>roleDefinitionCode</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>roleDefinitionId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### Beskjed

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#mottaker">Mottaker</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>mottakere</strong></td>
<td valign="top">[<a href="#mottaker">Mottaker</a>!]!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>metadata</strong></td>
<td valign="top"><a href="#metadata">Metadata</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>beskjed</strong></td>
<td valign="top"><a href="#beskjeddata">BeskjedData</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#eksterntvarsel">EksterntVarsel</a>!]!</td>
<td></td>
</tr>
</tbody>
</table>

#### BeskjedData

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>merkelapp</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelapp for beskjeden. Er typisk navnet på ytelse eller lignende. Den vises til brukeren.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>tekst</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Teksten som vises til brukeren.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>lenke</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Lenken som brukeren føres til hvis de klikker på beskjeden.

</td>
</tr>
</tbody>
</table>

#### DuplikatEksternIdOgMerkelapp

Denne feilen returneres dersom du prøver å opprette en notifikasjon med en eksternId og merkelapp som allerede finnes

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### DuplikatGrupperingsid

Denne feilen returneres hvis det allerede eksisterer en sak med denne grupperingsid-en under
merkelappen.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### EksterntVarsel

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>status</strong></td>
<td valign="top"><a href="#eksterntvarselstatus">EksterntVarselStatus</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### HardDeleteNotifikasjonVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en til oppgaven du "hard-delete"-et.

</td>
</tr>
</tbody>
</table>

#### HardDeleteSakVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en til saken du "hard-delete"-et.

</td>
</tr>
</tbody>
</table>

#### Konflikt

Oppgitt informasjon samsvarer ikke med tidligere informasjon som er oppgitt.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### Metadata

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksternId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>opprettetTidspunkt</strong></td>
<td valign="top"><a href="#iso8601datetime">ISO8601DateTime</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>grupperingsid</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>softDeleted</strong></td>
<td valign="top"><a href="#boolean">Boolean</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>softDeletedAt</strong></td>
<td valign="top"><a href="#iso8601datetime">ISO8601DateTime</a></td>
<td></td>
</tr>
</tbody>
</table>

#### NaermesteLederMottaker

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>naermesteLederFnr</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>ansattFnr</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>virksomhetsnummer</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### NotifikasjonConnection

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>edges</strong></td>
<td valign="top">[<a href="#notifikasjonedge">NotifikasjonEdge</a>!]!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>pageInfo</strong></td>
<td valign="top"><a href="#pageinfo">PageInfo</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### NotifikasjonEdge

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>node</strong></td>
<td valign="top"><a href="#notifikasjon">Notifikasjon</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>cursor</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### NotifikasjonFinnesIkke

Denne feilen returneres dersom du prøver å referere til en notifikasjon
som ikke eksisterer.

Utover at dere kan ha oppgitt feil informasjon, så kan det potensielt være på grunn
av "eventual consistency" i systemet vårt.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### NyBeskjedVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#nyeksterntvarselresultat">NyEksterntVarselResultat</a>!]!</td>
<td></td>
</tr>
</tbody>
</table>

#### NyEksterntVarselResultat

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### NyOppgaveVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#nyeksterntvarselresultat">NyEksterntVarselResultat</a>!]!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>paaminnelse</strong></td>
<td valign="top"><a href="#paaminnelseresultat">PaaminnelseResultat</a></td>
<td></td>
</tr>
</tbody>
</table>

#### NySakVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### NyStatusSakVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>statuser</strong></td>
<td valign="top">[<a href="#statusoppdatering">StatusOppdatering</a>!]!</td>
<td>

Nyeste statusoppdatering er først i listen.

</td>
</tr>
</tbody>
</table>

#### Oppgave

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#mottaker">Mottaker</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>mottakere</strong></td>
<td valign="top">[<a href="#mottaker">Mottaker</a>!]!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>metadata</strong></td>
<td valign="top"><a href="#metadata">Metadata</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>oppgave</strong></td>
<td valign="top"><a href="#oppgavedata">OppgaveData</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#eksterntvarsel">EksterntVarsel</a>!]!</td>
<td></td>
</tr>
</tbody>
</table>

#### OppgaveData

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>tilstand</strong></td>
<td valign="top"><a href="#oppgavetilstand">OppgaveTilstand</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>merkelapp</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelapp for beskjeden. Er typisk navnet på ytelse eller lignende. Den vises til brukeren.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>tekst</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Teksten som vises til brukeren.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>lenke</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Lenken som brukeren føres til hvis de klikker på beskjeden.

</td>
</tr>
</tbody>
</table>

#### OppgaveUtfoertVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en til oppgaven du oppdaterte.

</td>
</tr>
</tbody>
</table>

#### OppgaveUtgaattVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en til oppgaven du oppdaterte.

</td>
</tr>
</tbody>
</table>

#### OppgavenErAlleredeUtfoert

Denne feilen returneres dersom du forsøker å gå fra utført til utgått.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### PaaminnelseResultat

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#nyeksterntvarselresultat">NyEksterntVarselResultat</a>!]!</td>
<td></td>
</tr>
</tbody>
</table>

#### PageInfo

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>hasNextPage</strong></td>
<td valign="top"><a href="#boolean">Boolean</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>endCursor</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### SakFinnesIkke

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### SoftDeleteNotifikasjonVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en til oppgaven du "soft-delete"-et.

</td>
</tr>
</tbody>
</table>

#### SoftDeleteSakVellykket

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>id</strong></td>
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en til saken du "soft-delete"-et.

</td>
</tr>
</tbody>
</table>

#### StatusOppdatering

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>status</strong></td>
<td valign="top"><a href="#saksstatus">SaksStatus</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>tidspunkt</strong></td>
<td valign="top"><a href="#iso8601datetime">ISO8601DateTime</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>overstyrStatusTekstMed</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td></td>
</tr>
</tbody>
</table>

#### UgyldigMerkelapp

Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### UgyldigMottaker

Denne feilen returneres dersom en produsent forsøker å benytte en mottaker som den ikke har tilgang til.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### UgyldigPaaminnelseTidspunkt

Tidpunkt for påminnelse er ugyldig iht grenseverdier. F.eks før opprettelse eller etter frist, eller i fortid.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### UkjentProdusent

Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### UkjentRolle

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

### Inputs

#### AltinnMottakerInput

Spesifiser mottaker ved hjelp av tilganger i Altinn. Enhver som har den gitte tilgangen vil
kunne se notifikasjone.

Tilgangssjekken utføres hver gang en bruker ser på notifikasjoner. Det betyr at hvis en
bruker mister en Altinn-tilgang, så vil de hverken se historiske eller nye notifikasjone knyttet til den Altinn-tilgangen.
Og motsatt, hvis en bruker får en Altinn-tilgang, vil de se tidligere notifikasjoner for den Altinn-tilgangen.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>serviceCode</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>serviceEdition</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### AltinnReporteeMottakerInput

Spesifiser mottaker ved hjelp av fødselsnummer.
Flere skjemaer krever kun en "tilknytning" til et orgnr for å sende inn/se status.
Tilknytningen er da at man er "reportee" for virksomheten, uten å spesifisere noen enkeltrettighet.

Tilgangssjekken utføres hver gang en bruker ønsker se notifikasjonen. Dersom personen ikke lenger er tilknyttet
virksomheten så vil de hverken se historiske eller nye notifikasjoner knyttet til virksomheten.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>fnr</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### AltinnRolleMottakerInput

Spesifiser mottaker ved hjelp av rollekode.  Enhver som har den gitte rollen vil kunne se notifikasjonen.

Tilgangssjekken utføres hver gang en bruker ser på notifikasjoner. Det betyr at hvis en
bruker mister en Altinn-tilgang, så vil de hverken se historiske eller nye notifikasjone knyttet til den Altinn-tilgangen.
Og motsatt, hvis en bruker får en Altinn-rolle, vil de se tidligere notifikasjoner for den Altinn-tilgangen.

Tilgangssjekken utføres hver gang en bruker ønsker se notifikasjonen.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>roleDefinitionCode</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### EksterntVarselEpostInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#epostmottakerinput">EpostMottakerInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epostTittel</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Subject/emne til e-posten.
OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epostHtmlBody</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Kroppen til e-posten. Tolkes som HTML.
OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>sendetidspunkt</strong></td>
<td valign="top"><a href="#sendetidspunktinput">SendetidspunktInput</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### EksterntVarselInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>sms</strong></td>
<td valign="top"><a href="#eksterntvarselsmsinput">EksterntVarselSmsInput</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epost</strong></td>
<td valign="top"><a href="#eksterntvarselepostinput">EksterntVarselEpostInput</a></td>
<td></td>
</tr>
</tbody>
</table>

#### EksterntVarselSmsInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#smsmottakerinput">SmsMottakerInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>smsTekst</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Teksten som sendes i SMS-en.
OBS: Det er ikke lov med personopplysninger i teksten. SMS er ikke en sikker kanal.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>sendetidspunkt</strong></td>
<td valign="top"><a href="#sendetidspunktinput">SendetidspunktInput</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### EpostKontaktInfoInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>fnr</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

deprecated. value is ignored. 

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epostadresse</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### EpostMottakerInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>kontaktinfo</strong></td>
<td valign="top"><a href="#epostkontaktinfoinput">EpostKontaktInfoInput</a></td>
<td></td>
</tr>
</tbody>
</table>

#### FutureTemporalInput

Med denne kan dere spesifiserer et konkret tidspunkt.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>den</strong></td>
<td valign="top"><a href="#iso8601localdatetime">ISO8601LocalDateTime</a></td>
<td>

En konkret dato. I Europe/Oslo-tidssone.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>om</strong></td>
<td valign="top"><a href="#iso8601duration">ISO8601Duration</a></td>
<td>

Som duration-offset relativt til implisitt dato.  Dere må se
på dokumentasjonen til feltet hvor denne datatypen er brukt for
å vite hva vi bruker som implisitt dato.

</td>
</tr>
</tbody>
</table>

#### HardDeleteUpdateInput

Dersom dere vet at saken/notifikasjonen senere skal slettes helt kan det angis her.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>nyTid</strong></td>
<td valign="top"><a href="#futuretemporalinput">FutureTemporalInput</a>!</td>
<td>

Oppgi dersom dere ønsker at hard delete skal skeduleres. Vi
tolker relative datoer basert på tidspunkt angitt i kallet eller
når vi mottok kallet, hvis dere ikke har oppgitt det eller det
ikke er mulig å oppgi.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>strategi</strong></td>
<td valign="top"><a href="#nytidstrategi">NyTidStrategi</a>!</td>
<td>

hvis det finnes fremtidig sletting hvordan skal vi håndtere dette

</td>
</tr>
</tbody>
</table>

#### MetadataInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>virksomhetsnummer</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Hvilken virksomhet som skal motta notifikasjonen.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksternId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Den eksterne id-en brukes for å unikt identifisere en notifikasjon. Den må være unik for merkelappen.

Hvis dere har en enkel, statisk bruk av notifikasjoner, så kan dere utlede eksternId
fra f.eks. et saksnummer, og på den måten kunne referere til notifikasjoner dere har opprettet,
uten at dere må lagre ID-ene vi genererer og returnerer til dere.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>opprettetTidspunkt</strong></td>
<td valign="top"><a href="#iso8601datetime">ISO8601DateTime</a></td>
<td>

Hvilken dato vi viser til brukeren. Dersom dere ikke oppgir noen dato, så
bruker vi tidspuktet dere gjør kallet på.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>grupperingsid</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Grupperings-id-en gjør det mulig å knytte sammen forskjellige oppgaver, beskjed og saker.
Det vises ikke til brukere.
Saksnummer er en naturlig grupperings-id.

Når dere bruker grupperings-id, så er det mulig for oss å presentere en tidslinje
med alle notifikasjonene og status-oppdateringer knyttet til en sak.

Vi kan også vurdere å implemntere API-er for dere, slik at dere enkelt kan slette
all informasjon knyttet til en sak i et enkelt kall (f.eks. for å ivareta personvern).
Ta kontakt med #arbeidsgiver-notifikasjon på slack for å melde interesse!

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>hardDelete</strong></td>
<td valign="top"><a href="#futuretemporalinput">FutureTemporalInput</a></td>
<td>

Oppgi dersom dere ønsker at hard delete skal skeduleres. Vi
tolker relative datoer basert på `opprettetTidspunkt` (eller
når vi mottok kallet hvis dere ikke har oppgitt `opprettetTidspunkt`).

</td>
</tr>
</tbody>
</table>

#### MottakerInput

Hvem som skal se notifikasjonen.

Du kan spesifisere mottaker av notifikasjoner på forskjellige måter. Du skal bruke nøyaktig ett av feltene.

Vi har implementert det på denne måten fordi GraphQL ikke støtter union-typer som input.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>altinn</strong></td>
<td valign="top"><a href="#altinnmottakerinput">AltinnMottakerInput</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>naermesteLeder</strong></td>
<td valign="top"><a href="#naermesteledermottakerinput">NaermesteLederMottakerInput</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>altinnRolle</strong></td>
<td valign="top"><a href="#altinnrollemottakerinput">AltinnRolleMottakerInput</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>altinnReportee</strong></td>
<td valign="top"><a href="#altinnreporteemottakerinput">AltinnReporteeMottakerInput</a></td>
<td></td>
</tr>
</tbody>
</table>

#### NaermesteLederMottakerInput

Spesifiser mottaker ved hjelp av fødselsnummer. Fødselsnummeret er det til nærmeste leder. Det er kun denne personen
som potensielt kan se notifikasjonen. Det er videre en sjekk for å se om denne personen fortsatt er nærmeste leder
for den ansatte notifikasjonen gjelder.

Tilgangssjekken utføres hver gang en bruker ønsker se notifikasjonen.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>naermesteLederFnr</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>ansattFnr</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### NotifikasjonInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>merkelapp</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Merkelapp for beskjeden. Er typisk navnet på ytelse eller lignende. Den vises til brukeren.

Hva du kan oppgi som merkelapp er bestemt av produsent-registeret.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>tekst</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Teksten som vises til brukeren.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>lenke</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Lenken som brukeren føres til hvis de klikker på beskjeden.

</td>
</tr>
</tbody>
</table>

#### NyBeskjedInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#mottakerinput">MottakerInput</a></td>
<td>

Se dokumentasjonen til `mottakere`-feltet.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>mottakere</strong></td>
<td valign="top">[<a href="#mottakerinput">MottakerInput</a>!]!</td>
<td>

Her bestemmer dere hvem som skal få se notifikasjonen.

Hvis dere oppgir en mottaker i `mottaker`-feltet, så tolker vi det som om det var et element
i denne listen over mottakere.

Dere må gi oss minst 1 mottaker.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>notifikasjon</strong></td>
<td valign="top"><a href="#notifikasjoninput">NotifikasjonInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>metadata</strong></td>
<td valign="top"><a href="#metadatainput">MetadataInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#eksterntvarselinput">EksterntVarselInput</a>!]!</td>
<td></td>
</tr>
</tbody>
</table>

#### NyOppgaveInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#mottakerinput">MottakerInput</a></td>
<td>

Se dokumentasjonen til `mottakere`-feltet.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>mottakere</strong></td>
<td valign="top">[<a href="#mottakerinput">MottakerInput</a>!]!</td>
<td>

Her bestemmer dere hvem som skal få se notifikasjonen.

Hvis dere oppgir en mottaker i `mottaker`-feltet, så tolker vi det som om det var et element
i denne listen over mottakere.

Dere må gi oss minst 1 mottaker.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>notifikasjon</strong></td>
<td valign="top"><a href="#notifikasjoninput">NotifikasjonInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>frist</strong></td>
<td valign="top"><a href="#iso8601date">ISO8601Date</a></td>
<td>

Her kan du spesifisere frist for når oppgaven skal utføres av bruker.
Ideen er at etter fristen, så har ikke bruker lov, eller dere sperret for,
å gjøre oppgaven.

Fristen vises til bruker i grensesnittet.
Oppgaven blir automatisk markert som `UTGAAT` når fristen er forbi.
Dere kan kun oppgi frist med dato, og ikke klokkelsett.
Fristen regnes som utløpt når dagen er omme (midnatt, norsk tidssone).

Hvis dere ikke sender med frist, så viser vi ingen frist for bruker,
og oppgaven anses som NY frem til dere markerer oppgaven som `UTFOERT` eller
`UTGAATT`.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>metadata</strong></td>
<td valign="top"><a href="#metadatainput">MetadataInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#eksterntvarselinput">EksterntVarselInput</a>!]!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>paaminnelse</strong></td>
<td valign="top"><a href="#paaminnelseinput">PaaminnelseInput</a></td>
<td>

Her kan du spesifisere en påminnelse for en oppgave.
Brukeren vil bli gjort oppmerksom via bjellen og evt ekstern varsling dersom du oppgir det.

</td>
</tr>
</tbody>
</table>

#### PaaminnelseEksterntVarselEpostInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#epostmottakerinput">EpostMottakerInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epostTittel</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Subject/emne til e-posten.
OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epostHtmlBody</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Kroppen til e-posten. Tolkes som HTML.
OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>sendevindu</strong></td>
<td valign="top"><a href="#sendevindu">Sendevindu</a>!</td>
<td>

Vi sender eposten med utgangspunkt i påminnelsestidspunktet, men tar hensyn
til sendingsvinduet. Hvis påminnelsestidspunktet er utenfor vinduet, sender vi
det ved første mulighet.

</td>
</tr>
</tbody>
</table>

#### PaaminnelseEksterntVarselInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>sms</strong></td>
<td valign="top"><a href="#paaminnelseeksterntvarselsmsinput">PaaminnelseEksterntVarselSmsInput</a></td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epost</strong></td>
<td valign="top"><a href="#paaminnelseeksterntvarselepostinput">PaaminnelseEksterntVarselEpostInput</a></td>
<td></td>
</tr>
</tbody>
</table>

#### PaaminnelseEksterntVarselSmsInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>mottaker</strong></td>
<td valign="top"><a href="#smsmottakerinput">SmsMottakerInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>smsTekst</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Teksten som sendes i SMS-en.
OBS: Det er ikke lov med personopplysninger i teksten. SMS er ikke en sikker kanal.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>sendevindu</strong></td>
<td valign="top"><a href="#sendevindu">Sendevindu</a>!</td>
<td>

Vi sender SMS-en med utgangspunkt i påminnelsestidspunktet, men tar hensyn
til sendingsvinduet. Hvis påminnelsestidspunktet er utenfor vinduet, sender vi
det ved første mulighet.

</td>
</tr>
</tbody>
</table>

#### PaaminnelseInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>tidspunkt</strong></td>
<td valign="top"><a href="#paaminnelsetidspunktinput">PaaminnelseTidspunktInput</a>!</td>
<td>

Tidspunktet for når påminnelsen skal aktiveres.
Dersom det er angitt frist må påminnelsen være før dette.

Hvis du sender `eksterneVarsler`, så vil vi sjekke at vi har
mulighet for å sende dem før fristen, ellers får du feil ved
opprettelse av oppgaven.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>eksterneVarsler</strong></td>
<td valign="top">[<a href="#paaminnelseeksterntvarselinput">PaaminnelseEksterntVarselInput</a>!]!</td>
<td></td>
</tr>
</tbody>
</table>

#### PaaminnelseTidspunktInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>konkret</strong></td>
<td valign="top"><a href="#iso8601localdatetime">ISO8601LocalDateTime</a></td>
<td>

Konkret tidspunkt

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>etterOpprettelse</strong></td>
<td valign="top"><a href="#iso8601duration">ISO8601Duration</a></td>
<td>

Relativ til når oppgaven er angitt som opprettet. Altså X duration etter opprettelse.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>foerFrist</strong></td>
<td valign="top"><a href="#iso8601duration">ISO8601Duration</a></td>
<td>

Relativ til oppgavens frist, alts X duration før frist. Anses som ugyldig dersom oppgaven ikke har frist.

</td>
</tr>
</tbody>
</table>

#### SendetidspunktInput

Med denne typen velger du når du ønsker at det eksterne varselet blir sendt.
Du skal velge en (og kun en) av feltene, ellers blir forespørselen din avvist
med en feil.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>tidspunkt</strong></td>
<td valign="top"><a href="#iso8601localdatetime">ISO8601LocalDateTime</a></td>
<td>

Hvis du spesifiserer et tidspunkt på formen "YYYY-MM-DDThh:mm", så sender
vi notifikasjonen på det tidspunktet. Oppgir du et tidspunkt i fortiden,
så sender vi varselet øyeblikkelig.

Tidspunktet tolker vi som lokal, norsk tid (veggklokke-tid).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>sendevindu</strong></td>
<td valign="top"><a href="#sendevindu">Sendevindu</a></td>
<td></td>
</tr>
</tbody>
</table>

#### SmsKontaktInfoInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>fnr</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

deprecated. value is ignored. 

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>tlf</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

#### SmsMottakerInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>kontaktinfo</strong></td>
<td valign="top"><a href="#smskontaktinfoinput">SmsKontaktInfoInput</a></td>
<td></td>
</tr>
</tbody>
</table>

### Enums

#### EksterntVarselStatus

<table>
<thead>
<th align="left">Value</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong>NY</strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong>SENDT</strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong>FEILET</strong></td>
<td></td>
</tr>
</tbody>
</table>

#### NyTidStrategi

<table>
<thead>
<th align="left">Value</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong>FORLENG</strong></td>
<td>

Vi bruker den tiden som er lengst i fremtiden.

</td>
</tr>
<tr>
<td valign="top"><strong>OVERSKRIV</strong></td>
<td>

Vi bruker den nye tiden uansett.

</td>
</tr>
</tbody>
</table>

#### OppgaveTilstand

Tilstanden til en oppgave.

<table>
<thead>
<th align="left">Value</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong>NY</strong></td>
<td>

En oppgave som kan utføres.

</td>
</tr>
<tr>
<td valign="top"><strong>UTFOERT</strong></td>
<td>

En oppgave som allerede er utført.

</td>
</tr>
<tr>
<td valign="top"><strong>UTGAATT</strong></td>
<td>

En oppgave hvor frist har utgått.

</td>
</tr>
</tbody>
</table>

#### SaksStatus

Statusen påvirker bl.a. hvilket ikon som vises og brukes bl.a.
for å kunne filtrere saksoversikten på min side arbeidsgiver.

<table>
<thead>
<th align="left">Value</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong>MOTTATT</strong></td>
<td>

Naturlig start-tilstand for en sak.

Default tekst som vises til bruker: "Mottatt"

</td>
</tr>
<tr>
<td valign="top"><strong>UNDER_BEHANDLING</strong></td>
<td>

Default tekst som vises til bruker: "Under behandling"

</td>
</tr>
<tr>
<td valign="top"><strong>FERDIG</strong></td>
<td>

Slutt-tilstand for en sak. Når en sak er `FERDIG`, så vil vi
nedprioritere visningen av den på min side arbeidsgivere.

Default tekst som vises til bruker: "Ferdig".

</td>
</tr>
</tbody>
</table>

#### Sendevindu

For SMS, så vil
[Altinns varslingsvindu](https://altinn.github.io/docs/utviklingsguider/varsling/#varslingsvindu-for-sms)
også gjelde. Dette burde kun påvirke `LOEPENDE`.

<table>
<thead>
<th align="left">Value</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong>NKS_AAPNINGSTID</strong></td>
<td>

Vi sender varselet slik at mottaker skal ha mulighet for å
kontakte NAVs kontaktsenter (NKS) når de mottar varselet. Varsler
blir sendt litt før NKS åpner, og vi slutter å sende litt før
NKS stenger.

Vi tar foreløpig ikke hensyn til røde dager eller produksjonshendelser som fører til
at NKS er utilgjengelig.

</td>
</tr>
<tr>
<td valign="top"><strong>DAGTID_IKKE_SOENDAG</strong></td>
<td>

Vi sender varselet på dagtid, mandag til lørdag.
Altså sender vi ikke om kvelden og om natten, og ikke i det hele tatt på søndager.

Vi tar ikke hensyn til røde dager.

</td>
</tr>
<tr>
<td valign="top"><strong>LOEPENDE</strong></td>
<td>

Vi sender varslet så fort vi kan.

</td>
</tr>
</tbody>
</table>

### Scalars

#### Boolean

The `Boolean` scalar type represents `true` or `false`.

#### ID

The `ID` scalar type represents a unique identifier, often used to refetch an object or as key for a cache. The ID type appears in a JSON response as a String; however, it is not intended to be human-readable. When expected as an input type, any string (such as `"4"`) or integer (such as `4`) input value will be accepted as an ID.

#### ISO8601Date

Dato etter ISO8601-standaren. F.eks. `2020-01-02`, altså 2. mars 2020.

#### ISO8601DateTime

DateTime med offset etter ISO8601-standaren. F.eks. '2011-12-03T10:15:30+01:00'.

Er representert som String.

#### ISO8601Duration

Duration ISO8601-standaren. F.eks. 'P2DT3H4M'.

Er representert som String.

#### ISO8601LocalDateTime

Dato og lokaltid etter ISO8601-standaren. F.eks. '2001-12-24T10:44:01'.
Vi tolker tidspunktet som Oslo-tid ('Europe/Oslo').

#### Int

The `Int` scalar type represents non-fractional signed whole numeric values. Int can represent values between -(2^31) and 2^31 - 1.

#### String

The `String` scalar type represents textual data, represented as UTF-8 character sequences. The String type is most often used by GraphQL to represent free-form human-readable text.


### Interfaces


#### Error

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong>feilmelding</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
</tbody>
</table>

### Unions

#### HardDeleteNotifikasjonResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#harddeletenotifikasjonvellykket">HardDeleteNotifikasjonVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#notifikasjonfinnesikke">NotifikasjonFinnesIkke</a></strong></td>
<td valign="top">Denne feilen returneres dersom du prøver å referere til en notifikasjon
som ikke eksisterer.

Utover at dere kan ha oppgitt feil informasjon, så kan det potensielt være på grunn
av "eventual consistency" i systemet vårt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

#### HardDeleteSakResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#harddeletesakvellykket">HardDeleteSakVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#sakfinnesikke">SakFinnesIkke</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

#### MineNotifikasjonerResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#notifikasjonconnection">NotifikasjonConnection</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

#### Mottaker

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#altinnmottaker">AltinnMottaker</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#altinnreporteemottaker">AltinnReporteeMottaker</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#altinnrollemottaker">AltinnRolleMottaker</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#naermesteledermottaker">NaermesteLederMottaker</a></strong></td>
<td></td>
</tr>
</tbody>
</table>

#### Notifikasjon

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#beskjed">Beskjed</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#oppgave">Oppgave</a></strong></td>
<td></td>
</tr>
</tbody>
</table>

#### NyBeskjedResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#nybeskjedvellykket">NyBeskjedVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmottaker">UgyldigMottaker</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en mottaker som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#duplikateksternidogmerkelapp">DuplikatEksternIdOgMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom du prøver å opprette en notifikasjon med en eksternId og merkelapp som allerede finnes</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentrolle">UkjentRolle</a></strong></td>
<td></td>
</tr>
</tbody>
</table>

#### NyOppgaveResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#nyoppgavevellykket">NyOppgaveVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmottaker">UgyldigMottaker</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en mottaker som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#duplikateksternidogmerkelapp">DuplikatEksternIdOgMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom du prøver å opprette en notifikasjon med en eksternId og merkelapp som allerede finnes</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentrolle">UkjentRolle</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigpaaminnelsetidspunkt">UgyldigPaaminnelseTidspunkt</a></strong></td>
<td valign="top">Tidpunkt for påminnelse er ugyldig iht grenseverdier. F.eks før opprettelse eller etter frist, eller i fortid.</td>
</tr>
</tbody>
</table>

#### NySakResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#nysakvellykket">NySakVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmottaker">UgyldigMottaker</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en mottaker som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#duplikatgrupperingsid">DuplikatGrupperingsid</a></strong></td>
<td valign="top">Denne feilen returneres hvis det allerede eksisterer en sak med denne grupperingsid-en under
merkelappen.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentrolle">UkjentRolle</a></strong></td>
<td></td>
</tr>
</tbody>
</table>

#### NyStatusSakResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#nystatussakvellykket">NyStatusSakVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#sakfinnesikke">SakFinnesIkke</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#konflikt">Konflikt</a></strong></td>
<td valign="top">Oppgitt informasjon samsvarer ikke med tidligere informasjon som er oppgitt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

#### OppgaveUtfoertResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#oppgaveutfoertvellykket">OppgaveUtfoertVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#notifikasjonfinnesikke">NotifikasjonFinnesIkke</a></strong></td>
<td valign="top">Denne feilen returneres dersom du prøver å referere til en notifikasjon
som ikke eksisterer.

Utover at dere kan ha oppgitt feil informasjon, så kan det potensielt være på grunn
av "eventual consistency" i systemet vårt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

#### OppgaveUtgaattResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#oppgaveutgaattvellykket">OppgaveUtgaattVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#oppgaveneralleredeutfoert">OppgavenErAlleredeUtfoert</a></strong></td>
<td valign="top">Denne feilen returneres dersom du forsøker å gå fra utført til utgått.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#notifikasjonfinnesikke">NotifikasjonFinnesIkke</a></strong></td>
<td valign="top">Denne feilen returneres dersom du prøver å referere til en notifikasjon
som ikke eksisterer.

Utover at dere kan ha oppgitt feil informasjon, så kan det potensielt være på grunn
av "eventual consistency" i systemet vårt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

#### SoftDeleteNotifikasjonResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#softdeletenotifikasjonvellykket">SoftDeleteNotifikasjonVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#notifikasjonfinnesikke">NotifikasjonFinnesIkke</a></strong></td>
<td valign="top">Denne feilen returneres dersom du prøver å referere til en notifikasjon
som ikke eksisterer.

Utover at dere kan ha oppgitt feil informasjon, så kan det potensielt være på grunn
av "eventual consistency" i systemet vårt.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

#### SoftDeleteSakResultat

<table>
<thead>
<th align="left">Type</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong><a href="#softdeletesakvellykket">SoftDeleteSakVellykket</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ugyldigmerkelapp">UgyldigMerkelapp</a></strong></td>
<td valign="top">Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til.</td>
</tr>
<tr>
<td valign="top"><strong><a href="#sakfinnesikke">SakFinnesIkke</a></strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong><a href="#ukjentprodusent">UkjentProdusent</a></strong></td>
<td valign="top">Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt.</td>
</tr>
</tbody>
</table>

<!-- END graphql-markdown -->