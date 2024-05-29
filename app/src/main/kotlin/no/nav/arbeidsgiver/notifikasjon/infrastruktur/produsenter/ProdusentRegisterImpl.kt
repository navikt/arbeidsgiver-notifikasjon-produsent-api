package no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv

val FAGER_TESTPRODUSENT = Produsent(
    id = "fager",
    accessPolicy = basedOnEnv(
        prod = { listOf() },
        other = { listOf(
            "dev-gcp:fager:notifikasjon-test-produsent",
            "dev-gcp:fager:notifikasjon-test-produsent-v2",
        ) },
    ),
    tillatteMerkelapper = listOf(
        "fager",
        "fager2",
        "fager3",
        "Inntektsmelding",
        "Inntektsmelding sykepenger",
        "Inntektsmelding foreldrepenger",
        "Inntektsmelding svangerskapspenger",
        "Inntektsmelding omsorgspenger",
        "Inntektsmelding pleiepenger sykt barn",
        "Inntektsmelding pleiepenger i livets sluttfase",
        "Inntektsmelding opplæringspenger",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1"),
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig lønnstilskudd"),
        ServicecodeDefinisjon(code = "5516", version = "2", description = "Varig lønnstilskudd'"),
        ServicecodeDefinisjon(code = "5516", version = "3", description = "Sommerjobb'"),
        NærmesteLederDefinisjon,
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
                "dev-gcp:team-tiltak:tiltak-notifikasjon",
            )
        }
    ),
    tillatteMerkelapper = listOf(
        "Tiltak",
        "Lønnstilskudd",
        "Mentor",
        "Sommerjobb",
        "Arbeidstrening",
        "Inkluderingstilskudd",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
        ServicecodeDefinisjon(
            code = "5332",
            version = basedOnEnv(prod = { "2" }, other = { "1" }),
            description = "Arbeidstrening"
        ),
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig lønnstilskudd"),
        ServicecodeDefinisjon(code = "5516", version = "2", description = "Varig lønnstilskudd"),
        ServicecodeDefinisjon(code = "5516", version = "3", description = "Sommerjobb"),
        ServicecodeDefinisjon(code = "5516", version = "4", description = "Mentor"),
        ServicecodeDefinisjon(code = "5516", version = "5", description = "Inkluderingstilskudd"),
    )
)

val ESYFO = Produsent(
    id = "esyfovarsel",
    accessPolicy = basedOnEnv(
        prod = { listOf(
            "prod-gcp:team-esyfo:esyfovarsel",
        ) },
        other = { listOf(
            "dev-gcp:team-esyfo:esyfovarsel",
        ) },
    ),
    tillatteMerkelapper = listOf(
        "Dialogmøte",
        "Oppfølging",
    ),
    tillatteMottakere = listOf(NærmesteLederDefinisjon)
)

val PERMITTERING = Produsent(
    id = "permitteringsmelding-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = { listOf("prod-gcp:permittering-og-nedbemanning:permitteringsmelding-notifikasjon") },
        other = { listOf("dev-gcp:permittering-og-nedbemanning:permitteringsmelding-notifikasjon") },
    ),
    tillatteMerkelapper = listOf(
        "Permittering",
        "Nedbemanning",
        "Innskrenking av arbeidstid",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "5810", version = "1", description = "Innsyn i permittering- og nedbemanningsmeldinger sendt til NAV"),
    )
)

val FRITAKAGP = Produsent(
    id = "fritakagp",
    accessPolicy = basedOnEnv(
        prod = { listOf("prod-gcp:helsearbeidsgiver:fritakagp") },
        other = { listOf("dev-gcp:helsearbeidsgiver:fritakagp") },
    ),
    tillatteMerkelapper = listOf("Fritak arbeidsgiverperiode"),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding")
    )
)

val HELSEARBEIDSGIVER = Produsent(
    id = "helsearbeidsgiver",
    accessPolicy = basedOnEnv(
        prod = { listOf(
            "prod-gcp:helsearbeidsgiver:im-notifikasjon",
        ) },
        other = { listOf(
            "dev-gcp:helsearbeidsgiver:im-notifikasjon",
        ) },
    ),
    tillatteMerkelapper = listOf(
        "Inntektsmelding",
        "Inntektsmelding sykepenger"
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
    )
)

val TOI = Produsent(
    id = "toi",
    accessPolicy = basedOnEnv(
        prod = { listOf(
            "prod-gcp:toi:toi-arbeidsgiver-notifikasjon"
        ) },
        other = { listOf(
            "dev-gcp:toi:toi-arbeidsgiver-notifikasjon",
        ) },
    ),
    tillatteMerkelapper = listOf(
        "Kandidater",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "5078", version = "1", description = "Rekruttering")
    )
)

val ARBEIDSGIVERDIALOG = Produsent(
    id = "arbeidsgiver-dialog",
    accessPolicy = basedOnEnv(
        prod = { listOf(
            "prod-external:teamcrm:salesforce",
            "prod-gcp:teamcrm:saas-proxy",
        )},
        other = { listOf(
            "dev-external:teamcrm:salesforce",
            "dev-gcp:teamcrm:saas-proxy",
        )},
    ),
    tillatteMerkelapper = listOf(
        "Lønnstilskudd",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig Lønnstilskudd")
    )
)

val YRKESSKADE = Produsent(
    id = "yrkesskade-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = { listOf(
            "prod-gcp:yrkesskade:yrkesskade-melding-mottak",
        )},
        other = { listOf(
            "dev-gcp:yrkesskade:yrkesskade-melding-mottak",
        )},
    ),
    tillatteMerkelapper = listOf(
        "Skademelding",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "5902", version = "1", description = "Skademelding ved arbeidsulykke eller yrkessykdom")
    )
)

val FORELDREPENGER = Produsent(
    id = "fp-inntektsmelding-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = { listOf() },
        other = { listOf(
            "dev-fss:teamforeldrepenger:ftinntektsmelding",
        )},
    ),
    tillatteMerkelapper = listOf(
        "Inntektsmelding foreldrepenger",
        "Inntektsmelding svangerskapspenger",
        "Inntektsmelding omsorgspenger",
        "Inntektsmelding pleiepenger sykt barn",
        "Inntektsmelding pleiepenger i livets sluttfase",
        "Inntektsmelding opplæringspenger",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding")
    )
)

val K9 = Produsent(
    id = "k9-inntektsmelding-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = { listOf() },
        other = { listOf(
            "dev-gcp:k9saksbehandling:k9-inntektsmelding",
        )},
    ),
    tillatteMerkelapper = listOf(
        "Inntektsmelding omsorgspenger",
        "Inntektsmelding pleiepenger sykt barn",
        "Inntektsmelding pleiepenger i livets sluttfase",
        "Inntektsmelding opplæringspenger",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding")
    )
)


val PRODUSENT_LIST =
    listOf(
        FAGER_TESTPRODUSENT,
        ARBEIDSGIVER_TILTAK,
        ESYFO,
        PERMITTERING,
        FRITAKAGP,
        HELSEARBEIDSGIVER,
        TOI,
        ARBEIDSGIVERDIALOG,
        YRKESSKADE,
        FORELDREPENGER,
        K9,
    )
        .filter { it.accessPolicy.isNotEmpty() }

val PRODUSENT_REGISTER by lazy { ProdusentRegisterImpl(PRODUSENT_LIST) }

val MOTTAKER_REGISTER: List<MottakerDefinisjon> by lazy {
    PRODUSENT_LIST
        .flatMap { it.tillatteMottakere }
        .distinct()
}
