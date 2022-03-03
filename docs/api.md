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

## GraphQL Schema Types

<!-- START graphql-markdown -->

<details>
  <summary><strong>Table of Contents</strong></summary>

  * [Query](#query)
  * [Mutation](#mutation)
  * [Objects](#objects)
    * [AltinnMottaker](#altinnmottaker)
    * [Beskjed](#beskjed)
    * [BeskjedData](#beskjeddata)
    * [DuplikatEksternIdOgMerkelapp](#duplikateksternidogmerkelapp)
    * [EksterntVarsel](#eksterntvarsel)
    * [HardDeleteNotifikasjonVellykket](#harddeletenotifikasjonvellykket)
    * [Metadata](#metadata)
    * [NaermesteLederMottaker](#naermesteledermottaker)
    * [NotifikasjonConnection](#notifikasjonconnection)
    * [NotifikasjonEdge](#notifikasjonedge)
    * [NotifikasjonFinnesIkke](#notifikasjonfinnesikke)
    * [NyBeskjedVellykket](#nybeskjedvellykket)
    * [NyEksterntVarselResultat](#nyeksterntvarselresultat)
    * [NyOppgaveVellykket](#nyoppgavevellykket)
    * [Oppgave](#oppgave)
    * [OppgaveData](#oppgavedata)
    * [OppgaveUtfoertVellykket](#oppgaveutfoertvellykket)
    * [PageInfo](#pageinfo)
    * [SoftDeleteNotifikasjonVellykket](#softdeletenotifikasjonvellykket)
    * [UgyldigMerkelapp](#ugyldigmerkelapp)
    * [UgyldigMottaker](#ugyldigmottaker)
    * [UkjentProdusent](#ukjentprodusent)
  * [Inputs](#inputs)
    * [AltinnMottakerInput](#altinnmottakerinput)
    * [EksterntVarselEpostInput](#eksterntvarselepostinput)
    * [EksterntVarselInput](#eksterntvarselinput)
    * [EksterntVarselSmsInput](#eksterntvarselsmsinput)
    * [EpostKontaktInfoInput](#epostkontaktinfoinput)
    * [EpostMottakerInput](#epostmottakerinput)
    * [MetadataInput](#metadatainput)
    * [MottakerInput](#mottakerinput)
    * [NaermesteLederMottakerInput](#naermesteledermottakerinput)
    * [NotifikasjonInput](#notifikasjoninput)
    * [NyBeskjedInput](#nybeskjedinput)
    * [NyOppgaveInput](#nyoppgaveinput)
    * [SendetidspunktInput](#sendetidspunktinput)
    * [SmsKontaktInfoInput](#smskontaktinfoinput)
    * [SmsMottakerInput](#smsmottakerinput)
  * [Enums](#enums)
    * [EksterntVarselStatus](#eksterntvarselstatus)
    * [OppgaveTilstand](#oppgavetilstand)
    * [Sendevindu](#sendevindu)
  * [Scalars](#scalars)
    * [Boolean](#boolean)
    * [ID](#id)
    * [ISO8601DateTime](#iso8601datetime)
    * [ISO8601LocalDateTime](#iso8601localdatetime)
    * [Int](#int)
    * [String](#string)
  * [Interfaces](#interfaces)
    * [Error](#error)
  * [Unions](#unions)
    * [HardDeleteNotifikasjonResultat](#harddeletenotifikasjonresultat)
    * [MineNotifikasjonerResultat](#minenotifikasjonerresultat)
    * [Mottaker](#mottaker)
    * [Notifikasjon](#notifikasjon)
    * [NyBeskjedResultat](#nybeskjedresultat)
    * [NyOppgaveResultat](#nyoppgaveresultat)
    * [OppgaveUtfoertResultat](#oppgaveutfoertresultat)
    * [SoftDeleteNotifikasjonResultat](#softdeletenotifikasjonresultat)

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
<td colspan="2" valign="top"><strong>oppgaveUtfoertByEksternId</strong></td>
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
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en som *dere ga oss* da dere opprettet oppgaven med `nyOppgave`.

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
<td colspan="2" valign="top"><strong>softDeleteNotifikasjonByEksternId</strong></td>
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
<td valign="top"><a href="#id">ID</a>!</td>
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
<td colspan="2" valign="top"><strong>hardDeleteNotifikasjonByEksternId</strong></td>
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
<td valign="top"><a href="#id">ID</a>!</td>
<td>

ID-en som dere ga oss da dere opprettet notifikasjonen.

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
<tr>
<td colspan="2" valign="top"><strong>virksomhetsnummer</strong></td>
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
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong>epostHtmlBody</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
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
<td></td>
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
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
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
<tr>
<td colspan="2" valign="top"><strong>virksomhetsnummer</strong></td>
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
<td valign="top"><a href="#mottakerinput">MottakerInput</a>!</td>
<td></td>
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
<td valign="top"><a href="#mottakerinput">MottakerInput</a>!</td>
<td></td>
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
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
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
</tbody>
</table>

#### Sendevindu

<table>
<thead>
<th align="left">Value</th>
<th align="left">Description</th>
</thead>
<tbody>
<tr>
<td valign="top"><strong>NKS_AAPNINGSTID</strong></td>
<td>

Vi sender varselet slik at mottaker skal ha mulighet for å kontakte NAVs
kontaktsenter (NKS). Varsler blir sendt litt før NKS åpner, og vi slutter å
sende litt før NKS stenger.

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

#### ISO8601DateTime

DateTime med offset etter ISO8601-standaren. F.eks. '2011-12-03T10:15:30+01:00'.

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

<!-- END graphql-markdown -->