---
layout: page
title: Guide
permalink: /guide/
---
# Onboarding og tilganger

For å få tilgang til API-et må teamet ditt godta [vilkårene](vilkaar.md), ved å registerer dere i repoet [navikt/arbeidsgiver-notifikasjon-produsenter](https://github.com/navikt/arbeidsgiver-notifikasjon-produsenter).
Der er også tilgangsstyring spesifisert.

# Tilgangstyring av mottakere  

Du kan spesifisere mottakerene av notifikasjonen på to måter: basert på Altinn-tilgang og digisyfos nærmeste leder. Det er viktig å spesifisere mottaker riktig, så eventuelle personopplysninger kun vises til de med tjenestelig behov. Har dere behov for en annen måte å spesifisere mottakere på, så kontakt oss! 

__Altinn-tilgang__ 

Du kan sende en notifikasjon til alle med en gitt Altinn-tilgang (servicecode og serviceedition) i en virksomhet. Du må da oppgi: 

* virksomhetsnummer 
* service code i Altinn 
* service edition i Altinn 

Hver gang en arbeidsgiver logger inn i en NAV-tjeneste, vil vi sjekke hvilke tilganger de har, og vise de notifikasjonene de har tilgang til. 

__Dine sykemeldte__ 

Vi bruker digisyfo nærmeste leder konsept. 

__Hvor lenge vises notifikasjonen for mottakere og lagres i loggene?__ 

Oppgaver og beskjeder vises i bjella så lenge arbeidsgivere fortsatt har tilgang å se dem og notifikasjonen ikke er slettet. Dere som produsent må derfor vurdere hvor lenge notifikasjonen bør vises for mottakeren og lagres i loggene. Dere utfør sletting med hjelp av våre API-er for sletting, se [API dokumentasjon](https://navikt.github.io/arbeidsgiver-notifikasjon-produsent-api/api/). Vi jobber med funksjonalitet der produsenten vid opprettelse av en notifikasjon kan definere hvor lenge den skal vises for mottakeren og hvor lenge loggene skal lagres. 

# Varsler på e-post eller SMS

Når dere oppretter en notifikasjon velger dere om denne skal varsles eksternt i tillegg. SMS eller e-post skal ikke inneholde noen personopplysninger men si generelt hva varslet gjelder. T ex “_Du har en ny sykemelding. Logg inn på NAV på Min side – arbeidsgiver for å finne den_”. Se bruksvilkårene for fler detaljer.  

**Ekstern varsling med kontaktinformasjon:** 

Vi støtter ekstern varsling der dere som produsent har kontaktinformasjon (telefonnummer eller e-postadresse) + virksomhetsnummer til den som skal varsles.  

**Ekstern varsling basert på service code I Altinn - planlagt:** 

Vil dere sende eksternt varslet til arbeidsgiver som har tilgang til deres tjeneste basert på tilgang i Altinn? Ta kontakt med oss så vi kan prioritere utvikling av dette! 

# Hva vises i notifikasjonen?

Merkelapp bestems av produsenten og skal gjøre det tydelig for mottaker hvilken domene notifikasjonen er om. T ex sykemeldte eller tiltak. Fet skrift på meldingen betyr at brukern ikke klikket på lenken. Hvilken virksomhet notifikasjonen gjelder vises også.  

Utført oppgave vises med grå ikon i tilegg til teksten "oppgave utført". Beroende av hvordan dere valgt å spesifisere mottakere kan det vare flere i virksomheten som mottat samme oppgave. Oppgaven kan derfor være utført av en annen person en den brukeren som ser "oppgave utført".  

![](images/forklaring.png) 

Bjella med notifikasjoner er en egen NPM-pakke som hvert enkelt team i tilegg kan plassere i bedriftsmenyen i sin applikasjon (eller direkte i applikasjonen hvis dere ikke bruker bedriftsmenyn). Dette gjør det enklere for arbeidgsiver å kunne navigere mellom oppgaver og beskjeder i forskjellige applikasjoner uten å alltid må inom Min side - arbeidgsiver. 

# Eksempel på opprettelse av beskjed
Når dere lager en GraphQL-spørring burde dere bruke variabler for å skille
det statiske og dynamiske i spørringene.

```graphql
mutation OpprettNyBeskjed(
  $eksternId: String!
  $virksomhetsnummer: String!
  $lenke: String!
) {
  nyBeskjed(nyBeskjed: {
    metadata: {
      eksternId: $eksternId
    }
    mottaker: {
      altinn: {
        serviceCode: "1234"
        serviceEdition: "1"
        virksomhetsnummer: $virksomhetsnummer
      }
    }
    notifikasjon: {
      merkelapp: "EtSakssystem"
      tekst: "Du har fått svar på din søknad"
      lenke: $lenke
    }
  }) {
    __typename
    ... on NyBeskjedVellykket {
      id
    }
    ... on Error {
      feilmelding
    }
  }
}
```

Med variabler:
```json
{
  "eksternId": "1234-oppdatering",
  "virksomhetsnummer": "012345678",
  "lenke": "https://dev.nav.no/sakssystem/?sak=1234"
}
```

