package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.features.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.NonBlockingAltinnrettigheterProxyKlient

interface Altinn {
    suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>
    ): List<BrukerModel.Tilgang>
}

object AltinnImpl : Altinn {
    private val log = logger()

    private val timer = Health.meterRegistry.timer("altinn_klient_hent_alle_tilganger")

    private val klient = NonBlockingAltinnrettigheterProxyKlient(
        AltinnrettigheterProxyKlient(
            AltinnrettigheterProxyKlientConfig(
                ProxyConfig(
                    url = "http://altinn-rettigheter-proxy.arbeidsgiver/altinn-rettigheter-proxy/",
                    consumerId = "notifikasjon-bruker-api",
                ),
                AltinnConfig(
                    url = basedOnEnv(
                        prod = { "https://api-gw.oera.no" },
                        other = { "https://api-gw-q1.oera.no" },
                    ),
                    altinnApiKey = System.getenv("ALTINN_HEADER") ?: "default",
                    altinnApiGwApiKey = System.getenv("APIGW_HEADER") ?: "default",
                )
            )
        )
    )

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>
    ): List<BrukerModel.Tilgang> =
        timer.coRecord {
            coroutineScope {
                val alleTilganger = tjenester.map {
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
    ): List<BrukerModel.Tilgang> {
        val reporteeList = try {
            klient.hentOrganisasjoner(
                SelvbetjeningToken(selvbetjeningsToken),
                Subject(fnr),
                ServiceCode(serviceCode),
                ServiceEdition(serviceEdition),
                false
            )
        } catch (error: AltinnException) {
            if (error.proxyError.httpStatus == 400)
                return emptyList()
            else
                throw error
        } catch (error: Exception) {
            if (error.message?.contains("403") == true)
                return emptyList()
            else
                throw error
        }

        return reporteeList
            .filter { it.type != "Enterprise" }
            .filterNot { it.type == "Person" && it.organizationNumber == null }
            .filter {
                if (it.organizationNumber == null) {
                    log.warn("filtrerer ut reportee uten organizationNumber: organizationForm=${it.organizationForm} type=${it.type} status=${it.status}")
                    false
                } else {
                    true
                }
            }
            .map {
                BrukerModel.Tilgang(
                    virksomhet = it.organizationNumber!!,
                    servicecode = serviceCode,
                    serviceedition = serviceEdition
                )
            }
    }
}

fun AltinnrettigheterProxyKlientFallbackException.erDriftsforstyrrelse() : Boolean {
    return when ((cause as? ServerResponseException)?.response?.status) {
        HttpStatusCode.BadGateway,
        HttpStatusCode.GatewayTimeout,
        HttpStatusCode.ServiceUnavailable -> true
        else -> false
    }
}
