---
layout: page
title: Guide
permalink: /guide/
---

Når dere ønsker å opprette en notifikasjon, gjør et kall til GraphQL-endepunktet for produsenter ([klikk her for skjema](https://github.com/navikt/arbeidsgiver-notifikasjon-produsent-api/blob/main/app/src/main/resources/produsent.graphql)). Endepunktet er tilgangsstyrt basert på Azure AD-token og produsent-registeret som beskrevet over.


# Adressering av mottaker
Du kan spesifisere mottakerene av notifikasjonen på to måter: basert på Altinn-tilgang og digisyfos nærmeste leder. Det er viktig å spesifisere mottaker riktig, så eventuelle personopplysninger kun vises til de med tjenestelig behov. Har dere behov for en annen måte å spesifisere mottakere på, så kontakt oss!

## Altinn-tilgang
Du kan sende en notifikasjon til alle med en gitt Altinn-tilgang (servicecode og serviceedition) i en virksomhet. Du må da oppgi:

- virksomhetsnummer
- service code i Altinn
- service edition i Altinn

Hver gang en arbeidsgiver logger inn i en NAV-tjeneste, vil vi sjekke hvilke tilganger de har, og vise de notifikasjonene de har tilgang til.

## Digisyfo (nærmeste leder)
