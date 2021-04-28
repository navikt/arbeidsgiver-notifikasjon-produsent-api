Dette er en plattform som lar dere (NAV-systemer, produsenter) sender notifikasjoner til arbeidsgivere.

# Kortversjon
Tjenesten deres må autentisere seg med Azure AD, type server–server, som [beskrevet i nais-dokumentasjonen](https://doc.nais.io/security/auth/azure-ad/).

Legg deres system, med tilhørende merkelapper, til i produsent-registeret ([se filen her](https://github.com/navikt/arbeidsgiver-notifikasjon-produsent-api/blob/main/app/src/main/resources/produsent-register.json)). Bruk subject som produsent-navn.

Når dere ønsker å opprette en notifikasjon, gjør et kall til GraphQL-endepunktet for produsenter ([klikk her for skjema](https://github.com/navikt/arbeidsgiver-notifikasjon-produsent-api/blob/main/app/src/main/resources/produsent.graphqls)). Endepunktet er tilgangsstyrt basert på Azure AD-token og produsent-registeret som beskrevet over.

NB: Vi bruker GraphQL over HTTP. Man kan enkelt gjøre en vanlig HTTP-forespørsel med JSON hvis man ikke ønsker å bruke et GraphQL-bibliotek.
