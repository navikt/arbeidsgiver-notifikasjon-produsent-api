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
            //"prod-gcp:helsearbeidsgiver:im-notifikasjon",
        ) },
        other = { listOf(
            "dev-gcp:helsearbeidsgiver:im-notifikasjon",
        ) },
    ),
    tillatteMerkelapper = listOf(
        "Inntektsmelding",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding")
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
        prod = { listOf() },
        other = { listOf(
            "dev-external:teamcrm:salesforce",
        ) },
    ),
    tillatteMerkelapper = listOf(
        "Lønnstilskudd",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig Lønnstilskudd")
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
    )
        .filter { it.accessPolicy.isNotEmpty() }

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
