package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.common.utils.Pair
import java.util.Set

const val proxyUrl = "https://arbeidsgiver.dev.nav.no/altinn-rettigheter-proxy/"
const val fallBackUrl = "https://vg.no" //TODO finn riktig måte å fallbacke på i gcp
val altinnHeader: String = System.getenv("ALTINN_HEADER")
const val APIGwHeader = "String"


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

private val våreTjenester = mutableSetOf(
    Pair.of("5216", "1"),
    Pair.of("5212", "1"),
    Pair.of("5384", "1"),
    Pair.of("5159", "1"),
    Pair.of("4936", "1"),
    Pair.of("5332", "2"),
    Pair.of("5332", "1"),
    Pair.of("5441", "1"),
    Pair.of("5516", "1"),
    Pair.of("5516", "2"),
    Pair.of("3403", "2"),
    Pair.of("5078", "1"),
    Pair.of("5278", "1")
)

fun hentRettigheter(fnr: String, selvbetjeningsToken: String): List<Tilgang> {
    var tilganger: MutableList<Tilgang> = mutableListOf()
    våreTjenester.forEach { tjeneste ->
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