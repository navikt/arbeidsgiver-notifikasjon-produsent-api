package no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv

val FAGER_TESTPRODUSENT = Produsent(
    id = "fager",
    accessPolicy = basedOnEnv(
        prod = { listOf() },
        other = { listOf("dev-gcp:fager:notifikasjon-test-produsent") },
    ),
    tillatteMerkelapper = listOf(
        "fager",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1"),
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig lønnstilskudd"),
        ServicecodeDefinisjon(code = "5516", version = "2", description = "Varig lønnstilskudd'"),
        ServicecodeDefinisjon(code = "5516", version = "3", description = "Sommerjobb'"),
        NærmesteLederDefinisjon,
        AltinnReporteeDefinisjon,
        AltinnRolleDefinisjon("DAGL")
    )
)

val ARBEIDSGIVER_TILTAK = Produsent(
    id = "arbeidsgiver-tiltak",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-fss:arbeidsgiver:tiltaksgjennomforing-api",
            )
        },
        other = {
            listOf(
                "dev-gcp:arbeidsgiver:tiltak-refusjon-api",
                "dev-fss:arbeidsgiver:tiltaksgjennomforing-api",
            )
        }
    ),
    tillatteMerkelapper = listOf(
        "Tiltak",
        "Lønnstilskudd",
        "Mentor",
        "Sommerjobb",
        "Arbeidstrening",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
        ServicecodeDefinisjon(
            code = "5332",
            version = basedOnEnv(prod = { "2" }, other = { "1" }),
            description = "Arbeidstrening"
        ),
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig lønnstilskudd"),
        ServicecodeDefinisjon(code = "5516", version = "2", description = "Varig lønnstilskudd'"),
        ServicecodeDefinisjon(code = "5516", version = "3", description = "Sommerjobb'"),
    )
)

val ESYFO = Produsent(
    id = "esyfovarsel",
    accessPolicy = basedOnEnv(
        prod = { listOf("prod-fss:team-esyfo:esyfovarsel-job") },
        other = { listOf("dev-fss:team-esyfo:esyfovarsel-job") },
    ),
    tillatteMerkelapper = listOf("Aktivitetskrav"),
    tillatteMottakere = listOf(NærmesteLederDefinisjon)
)

val PERMITTERING = Produsent(
    id = "permittering-og-nedbemmaning",
    accessPolicy = basedOnEnv(
        prod = { listOf("dev-gcp:permittering-og-nedbemanning:permitteringsportal-api") },
        other = { listOf("prod-gcp:permittering-og-nedbemanning:permitteringsportal-api") },
    ),
    tillatteMerkelapper = listOf("Permittering"),
    tillatteMottakere = listOf(
        AltinnRolleDefinisjon("DAGL"),
        AltinnRolleDefinisjon("LEDE"),
        AltinnReporteeDefinisjon
    )
)

val PRODUSENT_LIST = basedOnEnv(
    prod = {
        listOf(
            ARBEIDSGIVER_TILTAK,
        )
    },
    other = {
        listOf(
            FAGER_TESTPRODUSENT,
            ARBEIDSGIVER_TILTAK,
            ESYFO,
            PERMITTERING,
        )
    }
)

val PRODUSENT_REGISTER by lazy { ProdusentRegisterImpl(PRODUSENT_LIST) }

val MOTTAKER_REGISTER: List<MottakerDefinisjon> by lazy {
    PRODUSENT_LIST
        .flatMap { it.tillatteMottakere }
        .distinct()
}

//    ServicecodeDefinisjon(code = "5216", version = "1", description = "Mentortilskudd"),
//    ServicecodeDefinisjon(code = "5212", version = "1", description = "Inkluderingstilskudd"),
//    ServicecodeDefinisjon(code = "5384", version = "1", description = "Ekspertbistand"),
//    ServicecodeDefinisjon(code = "5159", version = "1", description = "Lønnstilskudd"),
//    ServicecodeDefinisjon(code = "5332", version = "2", description = "Arbeidstrening"),
//    ServicecodeDefinisjon(code = "5441", version = "1", description = "Arbeidsforhold"),
//    ServicecodeDefinisjon(code = "3403", version = "2", description = "Sykfraværsstatistikk"),
//    ServicecodeDefinisjon(code = "5078", version = "1", description = "Rekruttering"),
//    ServicecodeDefinisjon(code = "5278", version = "1", description = "Tilskuddsbrev om NAV-tiltak"),
