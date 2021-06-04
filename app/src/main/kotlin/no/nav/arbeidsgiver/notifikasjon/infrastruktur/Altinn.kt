package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.QueryModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.NonBlockingAltinnrettigheterProxyKlient

interface Altinn {
    suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<QueryModel.Tilgang>
}

val VÅRE_TJENESTER = MottakerRegister.servicecodeDefinisjoner

object AltinnImpl : Altinn {
    private val log = logger()

    private val timer = Health.meterRegistry.timer("altinn_klient_hent_alle_tilganger")

    private val klient = NonBlockingAltinnrettigheterProxyKlient(
        AltinnrettigheterProxyKlient(
            AltinnrettigheterProxyKlientConfig(
                ProxyConfig(
                    url = "http://altinn-rettigheter-proxy/altinn-rettigheter-proxy/",
                    consumerId = "arbeidsgiver-arbeidsforhold-api",
                ),
                AltinnConfig(
                    url = "https://api-gw-q1.oera.no/", //TODO finn riktig måte å fallbacke på i gcp
                    altinnApiKey = System.getenv("ALTINN_HEADER") ?: "default",
                    altinnApiGwApiKey = System.getenv("APIGW_HEADER") ?: "default",
                )
            )
        )
    )

    override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<QueryModel.Tilgang> =
        timer.coRecord {
            coroutineScope {
                val alleTilganger = VÅRE_TJENESTER.map {
                    val (code, version) = it
                    async {
                        hentTilganger(fnr, code, version, selvbetjeningsToken)
                    }
                }
                return@coroutineScope alleTilganger.awaitAll().flatten()
            }
        }

    private suspend fun hentTilganger(
        fnr: String,
        serviceCode: String,
        serviceEdition: String,
        selvbetjeningsToken: String,
    ): List<QueryModel.Tilgang> {
        val reporteeList = try {
            klient.hentOrganisasjoner(
                SelvbetjeningToken(selvbetjeningsToken),
                Subject(fnr),
                ServiceCode(serviceCode),
                ServiceEdition(serviceEdition),
                false
            )
        } catch (error: Exception) {
            if (error.message?.contains("403") == true)
                return emptyList()
            else
                throw error
        }

        return reporteeList
            .filter { it.type != "Enterprise" }
            .filter {
                if (it.organizationNumber == null) {
                    log.warn("filtrerer ut reportee uten organizationNumber")
                    false
                } else {
                    true
                }
            }
            .map {
                QueryModel.Tilgang(
                    virksomhet = it.organizationNumber!!,
                    servicecode = serviceCode,
                    serviceedition = serviceEdition
                )
            }
    }
}
