---
layout: page
title: Guide
permalink: /guide/
---
# Onboarding og tilganger

For å få tilgang til API-et må teamet ditt godta [vilkårene](vilkaar.md), ved å registerer dere i repoet [navikt/arbeidsgiver-notifikasjon-produsenter](https://github.com/navikt/arbeidsgiver-notifikasjon-produsenter).
Der er også tilgangsstyring spesifisert.

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

