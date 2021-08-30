package no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter

val MOTTAKER_REGISTER: List<MottakerDefinisjon> = listOf(
    ServicecodeDefinisjon(code = "5216", version = "1", description = "Mentortilskudd"),
    ServicecodeDefinisjon(code = "5212", version = "1", description = "Inkluderingstilskudd"),
    ServicecodeDefinisjon(code = "5384", version = "1", description = "Ekspertbistand"),
    ServicecodeDefinisjon(code = "5159", version = "1", description = "Lønnstilskudd"),
    ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
    ServicecodeDefinisjon(code = "5332", version = "2", description = "Arbeidstrening"),
    ServicecodeDefinisjon(code = "5332", version = "1", description = "Arbeidstrening"),
    ServicecodeDefinisjon(code = "5441", version = "1", description = "Arbeidsforhold"),
    ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig lønnstilskudd"),
    ServicecodeDefinisjon(code = "5516", version = "2", description = "Varig lønnstilskudd'"),
    ServicecodeDefinisjon(code = "3403", version = "2", description = "Sykfraværsstatistikk"),
    ServicecodeDefinisjon(code = "5078", version = "1", description = "Rekruttering"),
    ServicecodeDefinisjon(code = "5278", version = "1", description = "Tilskuddsbrev om NAV-tiltak"),
)

val FAGER_TESTPRODUSENT =
    Produsent(
        accessPolicy = listOf(
            "dev-gcp:fager:test-produsent"
        ),
        tillatteMerkelapper = listOf(
            "tiltak",
            "sykemeldte",
            "rekruttering"
        ),
        tillatteMottakere = listOf(
            ServicecodeDefinisjon(code = "5216", version = "1"),
            ServicecodeDefinisjon(code = "5212", version = "1"),
            ServicecodeDefinisjon(code = "5384", version = "1"),
            ServicecodeDefinisjon(code = "5159", version = "1"),
            ServicecodeDefinisjon(code = "4936", version = "1"),
            ServicecodeDefinisjon(code = "5332", version = "2"),
            ServicecodeDefinisjon(code = "5332", version = "1"),
            ServicecodeDefinisjon(code = "5441", version = "1"),
            ServicecodeDefinisjon(code = "5516", version = "1"),
            ServicecodeDefinisjon(code = "5516", version = "2"),
            ServicecodeDefinisjon(code = "3403", version = "2"),
            ServicecodeDefinisjon(code = "5078", version = "1"),
            ServicecodeDefinisjon(code = "5278", version = "1"),
            NærmesteLederDefinisjon,
        )
    )

fun createProdusentRegister(): ProdusentRegister {
    return ProdusentRegisterImpl(
        listOf(
            FAGER_TESTPRODUSENT
        )
    )
}

val PRODUSENT_REGISTER by lazy { createProdusentRegister() }

