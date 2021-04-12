package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*

const val proxyUrl = "https://arbeidsgiver.dev.nav.no/altinn-rettigheter-proxy/"
const val fallBackUrl = "https://api-gw-q1.oera.no/" //TODO finn riktig måte å fallbacke på i gcp
val altinnHeader: String = System.getenv("ALTINN_HEADER") ?: "default"
val APIGwHeader:String = System.getenv("APIGW_HEADER") ?: "default"


data class Organisasjon(
    var Name: String? = null,
    var ParentOrganizationNumber: String? = null,
    var OrganizationNumber: String? = null,
    var OrganizationForm: String? = null,
    var Status: String? = null,
    var Type: String? = null
)

sealed class AltinnOppslagResultat

data class AltinnOppslagVellykket(val organisasjoner: List<Organisasjon>) : AltinnOppslagResultat()

object AltinnIngenRettigheter : AltinnOppslagResultat()


val proxyKlientConfig = AltinnrettigheterProxyKlientConfig(
    ProxyConfig("arbeidsgiver-arbeidsforhold-api", proxyUrl),
    AltinnConfig(fallBackUrl, altinnHeader, APIGwHeader)
)
val klient = AltinnrettigheterProxyKlient(proxyKlientConfig)

fun run(action: () -> List<AltinnReportee>) =
    try {
        action()
            .map {
                Organisasjon(
                    Name = it.name,
                    ParentOrganizationNumber = it.parentOrganizationNumber,
                    OrganizationNumber = it.organizationNumber,
                    OrganizationForm = it.organizationForm,
                    Status = it.status,
                    Type = it.type
                )
            }
            .let { AltinnOppslagVellykket(it) }
    } catch (error: Exception) {
        //logger.error("AG-ARBEIDSFORHOLD Klarte ikke hente organisasjoner fra altinn.", error)
        if (error.message?.contains("403") == true) AltinnIngenRettigheter else throw error
    }




fun hentOrganisasjonerBasertPaRettigheter(
    fnr: String,
    serviceKode: String,
    serviceEdition: String,
    selvbetjeningsToken: String
): AltinnOppslagResultat =
    run {
        klient.hentOrganisasjoner(
            SelvbetjeningToken(selvbetjeningsToken),
            Subject(fnr),
            ServiceCode(serviceKode),
            ServiceEdition(serviceEdition),
            false
        )
    }

private val vaareTjenester = mutableSetOf(
    "5216" to "1", // Mentortilskudd
    "5212" to "1", // Inkluderingstilskudd
    "5384" to "1", // Ekspertbistand
    "5159" to "1", // Lønnstilskudd
    "4936" to "1", // Inntektsmelding
    "5332" to "2", // Arbeidstrening
    "5332" to "1", // Arbeidstrening
    "5441" to "1", // Arbeidsforhold
    "5516" to "1", // Midlertidig lønnstilskudd
    "5516" to "2", // Varig lønnstilskudd'
    "3403" to "2", // Sykfraværsstatistikk
    "5078" to "1", // Rekruttering
    "5278" to "1"  // Tilskuddsbrev om NAV-tiltak
)
object AltinnClient{
    fun hentRettigheter(fnr: String, selvbetjeningsToken: String): List<Tilgang> {
        val tilganger: MutableList<Tilgang> = mutableListOf()
        vaareTjenester.forEach { tjeneste ->
            when (val oppslagsresultat =
                hentOrganisasjonerBasertPaRettigheter(fnr, tjeneste.first, tjeneste.second, selvbetjeningsToken)) {
                is AltinnOppslagVellykket -> {
                    oppslagsresultat.organisasjoner.filter { it.Type != "Enterprise" }.forEach { organisasjon ->
                        tilganger.add(
                            Tilgang(
                                organisasjon.OrganizationNumber!!,
                                tjeneste.first,
                                tjeneste.second
                            )
                        )
                    }
                }
            }
        }
        return tilganger
    }
}