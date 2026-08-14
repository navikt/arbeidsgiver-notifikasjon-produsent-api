# Om denne dokumentasjonen

Dokumentasjonen her publiseres til github pages og er tilgjengelig på https://navikt.github.io/arbeidsgiver-notifikasjon-produsent-api/
Dokumentasjonen er skrevet i html med unntak av API-dokumentasjonen som er generert vha spectaql.

# Hvordan oppdatere dokumentasjonen

## Oppdatering av API-dokumentasjon

API-dokumentasjonen (`api.html`) genereres **ikke** lenger automatisk av CI. Den må oppdateres manuelt
hver gang `produsent.graphql` endres.

CI-jobben `Platform: verify docs` kjører ved push og pull request og feiler bygget hvis `api.html`
ikke er i sync med GraphQL-skjemaet.

Kjør følgende kommando fra `docs/`-mappen og commit resultatet:

```bash
cd docs
pnpm install
pnpm run update-gqldoc
```

## Oppdatering av annen dokumentasjon

Denne dokumentasjonen er skrevet i html og kan oppdateres ved å endre på filene i `docs`-mappen.