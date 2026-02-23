package no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRessursMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.AppName
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.util.*

typealias Merkelapp = String

data class Produsent(
    val id: String,
    val accessPolicy: List<AppName>,
    val tillatteMerkelapper: List<Merkelapp> = emptyList(),
    val tillatteMottakere: List<MottakerDefinisjon> = emptyList()
) {

    fun kanSendeTil(merkelapp: Merkelapp): Boolean {
        return tillatteMerkelapper.contains(merkelapp)
    }

    fun kanSendeTil(mottaker: Mottaker): Boolean =
        tillatteMottakere.any { tillatMottaker ->
            tillatMottaker.akseptererMottaker(mottaker)
        }
}

sealed class MottakerDefinisjon {
    abstract fun akseptererMottaker(mottaker: Mottaker): Boolean
}

data class ServicecodeDefinisjon(
    val code: String,
    val version: String,
    val description: String? = null
) : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is AltinnMottaker ->
                mottaker.serviceCode == code && mottaker.serviceEdition == version

            else -> false
        }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is ServicecodeDefinisjon -> this.code == other.code && this.version == other.version
        else -> false
    }

    override fun hashCode(): Int = Objects.hash(code, version)
}

data class RessursIdDefinisjon(
    val ressursId: String,
) : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is AltinnRessursMottaker ->
                mottaker.ressursId == ressursId

            else -> false
        }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is RessursIdDefinisjon -> this.ressursId == other.ressursId
        else -> false
    }

    override fun hashCode(): Int = Objects.hash(ressursId)
}

object NærmesteLederDefinisjon : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is NærmesteLederMottaker -> true
            else -> false
        }
}

object MottakerRegister {
    val servicecodeDefinisjoner: List<ServicecodeDefinisjon>
        get() {
            return MOTTAKER_REGISTER.filterIsInstance<ServicecodeDefinisjon>()
        }

    val ressursIdDefinisjoner: List<RessursIdDefinisjon>
        get() {
            return MOTTAKER_REGISTER.filterIsInstance<RessursIdDefinisjon>()
        }

    fun erDefinert(mottakerDefinisjon: MottakerDefinisjon): Boolean =
        when (mottakerDefinisjon) {
            is ServicecodeDefinisjon -> servicecodeDefinisjoner.contains(mottakerDefinisjon)
            is NærmesteLederDefinisjon -> true
            is RessursIdDefinisjon -> ressursIdDefinisjoner.contains(mottakerDefinisjon)
        }
}

interface ProdusentRegister {
    fun finn(appName: String): Produsent?
}

class ProdusentRegisterImpl(
    produsenter: List<Produsent>
) : ProdusentRegister {

    val log = logger()

    init {
        produsenter.forEach { produsent ->
            produsent.tillatteMottakere.forEach {
                check(MottakerRegister.erDefinert(it)) {
                    "Ugyldig mottaker $it for produsent $produsent"
                }
            }
        }
    }

    private val produsenterByName: Map<AppName, Produsent> =
        produsenter
            .flatMap { produsent ->
                produsent.accessPolicy.map { appNavn -> Pair(appNavn, produsent) }
            }
            .toMap()

    override fun finn(appName: AppName): Produsent? {
        return produsenterByName[appName] ?: run {
            log.error(
                """
                |Fant ikke produsent for appName=$appName.
                |Gyldige appName er: \n${produsenterByName.keys.joinToString(prefix = "| - ", postfix = "\n")}
            """.trimMargin()
            )
            return null
        }
    }
}


val FAGER_TESTPRODUSENT = Produsent(
    id = "fager",
    accessPolicy = basedOnEnv(
        prod = { listOf() },
        other = {
            listOf(
                "dev-gcp:fager:notifikasjon-test-produsent",
                "dev-gcp:fager:notifikasjon-test-produsent-v2",
            )
        },
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
        RessursIdDefinisjon(ressursId = "test-fager"),
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
                "prod-gcp:team-tiltak:tiltak-notifikasjon",
            )
        },
        other = {
            listOf(
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
        "Varig tilrettelagt arbeid"
    ),
    tillatteMottakere = listOf(
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
        ServicecodeDefinisjon(
            code = "5516",
            version = "6",
            description = "Varig tilrettelagt arbeid i ordinær virksomhet"
        ),
    )
)

val ESYFO = Produsent(
    id = "esyfovarsel",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-gcp:team-esyfo:esyfovarsel",
            )
        },
        other = {
            listOf(
                "dev-gcp:team-esyfo:esyfovarsel",
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Dialogmøte",
        "Oppfølging",
    ),
    tillatteMottakere = listOf(NærmesteLederDefinisjon)
)

val PERMITTERING = Produsent(
    id = "permitteringsskjema-api",
    accessPolicy = basedOnEnv(
        prod = { listOf("prod-gcp:permittering-og-nedbemanning:permitteringsskjema-api") },
        other = { listOf("dev-gcp:permittering-og-nedbemanning:permitteringsskjema-api") },
    ),
    tillatteMerkelapper = listOf(
        "Permittering",
        "Nedbemanning",
        "Innskrenking av arbeidstid",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(
            code = "5810",
            version = "1",
            description = "Innsyn i permittering- og nedbemanningsmeldinger sendt til NAV"
        ),
        RessursIdDefinisjon(ressursId = "nav_permittering-og-nedbemmaning_innsyn-i-alle-innsendte-meldinger"),
    )
)

val FRITAKAGP = Produsent(
    id = "fritakagp",
    accessPolicy = basedOnEnv(
        prod = { listOf("prod-gcp:helsearbeidsgiver:fritakagp") },
        other = { listOf("dev-gcp:helsearbeidsgiver:fritakagp") },
    ),
    tillatteMerkelapper = listOf("Fritak arbeidsgiverperiode"),
    tillatteMottakere = basedOnEnv(
        prod = {
            listOf(
                ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding")
            )
        },
        other = {
            listOf(
                ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
                RessursIdDefinisjon(ressursId = "nav_sykepenger_fritak-arbeidsgiverperiode")
            )
        }
    )
)

val HELSEARBEIDSGIVER = Produsent(
    id = "helsearbeidsgiver",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-gcp:helsearbeidsgiver:im-notifikasjon",
                "prod-gcp:helsearbeidsgiver:hag-admin"
            )
        },
        other = {
            listOf(
                "dev-gcp:helsearbeidsgiver:im-notifikasjon",
                "dev-gcp:helsearbeidsgiver:hag-admin"
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Inntektsmelding",
        "Inntektsmelding sykepenger"
    ),
    tillatteMottakere = basedOnEnv(
        prod = {
            listOf(
                ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
            )
        },
        other = {
            listOf(
                ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
                RessursIdDefinisjon(ressursId = "nav_sykepenger_inntektsmelding")
            )
        }
    )
)

val TOI = Produsent(
    id = "toi",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-gcp:toi:toi-arbeidsgiver-notifikasjon"
            )
        },
        other = {
            listOf(
                "dev-gcp:toi:toi-arbeidsgiver-notifikasjon",
            )
        },
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
        prod = {
            listOf(
                "prod-external:teamcrm:salesforce",
                "prod-gcp:teamcrm:saas-proxy",
            )
        },
        other = {
            listOf(
                "dev-external:teamcrm:salesforce",
                "dev-gcp:teamcrm:saas-proxy",
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Lønnstilskudd",
        "Arbeidstrening",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig Lønnstilskudd"),
        ServicecodeDefinisjon(
            code = "5332",
            version = basedOnEnv(
                prod = { "2" },
                other = { "1" }
            ),
            description = "Arbeidstrening"
        )
    )
)

val YRKESSKADE = Produsent(
    id = "yrkesskade-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-gcp:yrkesskade:yrkesskade-melding-mottak",
                "prod-gcp:yrkesskade:yrkesskade-saksbehandling-backend",
            )
        },
        other = {
            listOf(
                "dev-gcp:yrkesskade:yrkesskade-melding-mottak",
                "dev-gcp:yrkesskade:yrkesskade-saksbehandling-backend",
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Skademelding",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(
            code = "5902",
            version = "1",
            description = "Skademelding ved arbeidsulykke eller yrkessykdom"
        )
    )
)

val FORELDREPENGER = Produsent(
    id = "fp-inntektsmelding-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-gcp:teamforeldrepenger:fpinntektsmelding"
            )
        },
        other = {
            listOf(
                "dev-gcp:teamforeldrepenger:fpinntektsmelding",
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Inntektsmelding foreldrepenger",
        "Inntektsmelding svangerskapspenger",
    ),
    tillatteMottakere = basedOnEnv(
        other = {
            listOf(
                ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
                RessursIdDefinisjon(ressursId = "nav_foreldrepenger_inntektsmelding")
            )
        },
        prod = {
            listOf(
                ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding")
            )
        }
    )
)

val K9 = Produsent(
    id = "k9-inntektsmelding-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-gcp:k9saksbehandling:k9-inntektsmelding"
            )
        },
        other = {
            listOf(
                "dev-gcp:k9saksbehandling:k9-inntektsmelding",
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Inntektsmelding omsorgspenger",
        "Inntektsmelding pleiepenger sykt barn",
        "Inntektsmelding pleiepenger i livets sluttfase",
        "Inntektsmelding opplæringspenger",
        "Refusjonskrav for omsorgspenger",
    ),
    tillatteMottakere = listOf(
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding")
    )
)

val MELOSYS = Produsent(
    id = "melosys-skjema-api-notifikasjon",
    accessPolicy = basedOnEnv(
        prod = { listOf() },
        other = {
            listOf(
                "dev-gcp:teammelosys:melosys-skjema-api",
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Utsendt arbeidstaker",
    ),
    tillatteMottakere = listOf(
        RessursIdDefinisjon(ressursId = "test-fager"),
    )
)

val EKSPERTBISTAND = Produsent(
    id = "ekspertbistand",
    accessPolicy = basedOnEnv(
        prod = {
            listOf(
                "prod-gcp:fager:ekspertbistand-backend",
            )
        },
        other = {
            listOf(
                "dev-gcp:fager:ekspertbistand-backend",
            )
        },
    ),
    tillatteMerkelapper = listOf(
        "Ekspertbistand",
    ),
    tillatteMottakere = listOf(
        RessursIdDefinisjon(ressursId = "nav_tiltak_ekspertbistand"),
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
        MELOSYS,
        EKSPERTBISTAND,
    )
        .filter { it.accessPolicy.isNotEmpty() }

val PRODUSENT_REGISTER by lazy { ProdusentRegisterImpl(PRODUSENT_LIST) }

val MOTTAKER_REGISTER: List<MottakerDefinisjon> by lazy {
    PRODUSENT_LIST
        .flatMap { it.tillatteMottakere }
        .distinct()
}