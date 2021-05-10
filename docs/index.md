Dette er en plattform som lar dere (NAV-systemer, produsenter av notifikasjoner) sender notifikasjoner til arbeidsgivere. Notifikasjonene dere sender vil være synlig for arbeidsgivere som er logget inn i NAV sine tjenester. Vi støtter ikke varsel på SMS eller e-post (men håper å støtte det i fremtiden).

# Kortversjon
Tjenesten deres må autentisere seg med Azure AD, type server–server, som [beskrevet i nais-dokumentasjonen](https://doc.nais.io/security/auth/azure-ad/).

Legg deres system, med tilhørende merkelapper, til i produsent-registeret ([se filen her](https://github.com/navikt/arbeidsgiver-notifikasjon-produsent-api/blob/main/app/src/main/resources/produsent-register.json)). Bruk subject som produsent-navn.

Når dere ønsker å opprette en notifikasjon, gjør et kall til GraphQL-endepunktet for produsenter ([klikk her for skjema](https://github.com/navikt/arbeidsgiver-notifikasjon-produsent-api/blob/main/app/src/main/resources/produsent.graphqls)). Endepunktet er tilgangsstyrt basert på Azure AD-token og produsent-registeret som beskrevet over.

NB: Vi bruker GraphQL over HTTP. Man kan enkelt gjøre en vanlig HTTP-forespørsel med JSON hvis man ikke ønsker å bruke et GraphQL-bibliotek.

# Adressering av mottaker
Du kan spesifisere mottakerene av notifikasjonen på to måter: basert på Altinn-tilgang og digisyfos nærmeste leder. Det er viktig å spesifisere mottaker riktig, så eventuelle personopplysninger kun vises til de med tjenestelig behov. Har dere behov for en annen måte å spesifisere mottakere på, så kontakt oss!

## Altinn-tilgang
Du kan sende en notifikasjon til alle med en gitt Altinn-tilgang (servicecode og serviceedition) i en virksomhet. Du må da oppgi:

- virksomhetsnummer
- service code i Altinn
- service edition i Altinn

Hver gang en arbeidsgiver logger inn i en NAV-tjeneste, vil vi sjekke hvilke tilganger de har, og vise de notifikasjonene de har tilgang til. 

## Digisyfo (nærmeste leder)
