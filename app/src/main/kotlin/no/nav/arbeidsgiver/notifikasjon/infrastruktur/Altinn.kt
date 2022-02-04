package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class AltinnRolle(
    val RoleDefinitionId: String,
    val RoleDefinitionCode: String
)

interface Altinn {
    suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>,
    ): List<BrukerModel.Tilgang>

    suspend fun hentRoller(
    ): List<AltinnRolle>
}

class AltinnImpl(
    private val klient: NonBlockingAltinnrettigheterProxyKlient = NonBlockingAltinnrettigheterProxyKlient(
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
) : Altinn {
    private val log = logger()

    private val timer = Health.meterRegistry.timer("altinn_klient_hent_alle_tilganger")

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>,
    ): List<BrukerModel.Tilgang> =
        timer.coRecord {
            coroutineScope {
                val alleTilganger = tjenester.map {
                    val (code, version) = it
                    async {
                        hentTilganger(fnr, code, version, selvbetjeningsToken)
                    }
                } + async {
                    hentTilganger(fnr, selvbetjeningsToken)
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
            when (error.proxyError.httpStatus) {
                400, 403 -> return emptyList()
                else -> throw error
            }
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
                BrukerModel.Tilgang.Altinn(
                    virksomhet = it.organizationNumber!!,
                    servicecode = serviceCode,
                    serviceedition = serviceEdition
                )
            }
    }

    private suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
    ): List<BrukerModel.Tilgang> {
        val reporteeList = try {
            klient.hentOrganisasjoner(
                SelvbetjeningToken(selvbetjeningsToken),
                Subject(fnr),
                true
            )
        } catch (error: AltinnException) {
            when (error.proxyError.httpStatus) {
                403 -> return emptyList()
                else -> throw error
            }
        } catch (error: Exception) {
            if (error.message?.contains("403") == true)
                return emptyList()
            else
                throw error
        }

        return reporteeList.map {
                BrukerModel.Tilgang.AltinnReportee(
                    virksomhet = it.organizationNumber!!,
                    fnr = fnr
                )
            }
    }

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    override suspend fun hentRoller(): List<AltinnRolle> {
        val baseUrl = basedOnEnv(
            prod = { "https://api-gw.oera.no" },
            other = { "https://api-gw-q1.oera.no" }
        )
        val altinnApiKey = System.getenv("ALTINN_HEADER") ?: "default"
        val altinnApiGwApiKey = System.getenv("APIGW_HEADER") ?: "default"
        val url = "${baseUrl}/ekstern/altinn/api/serviceowner/roledefinitions?ForceEIAuthentication&language=1044"

        try {
            return httpClient.get(url) {
                headers {
                    append("X-NAV-APIKEY", altinnApiGwApiKey)
                    append("APIKEY", altinnApiKey)
                }
            }
        } catch (e: ResponseException) {
            val melding = "Hent roller fra altinn feiler med " +
                    "${e.response.status.value} '${e.response.status.description}'"
            log.warn(melding)
            throw AltinnrettigheterProxyKlientFallbackException(melding, e)
        } catch (e: Exception) {
            val melding = "Fallback kall mot Altinn feiler med exception: '${e.message}' "
            log.warn(melding, e)
            throw AltinnrettigheterProxyKlientFallbackException(melding, e)
        }
    }
}

fun AltinnrettigheterProxyKlientFallbackException.erDriftsforstyrrelse(): Boolean {
    return when (cause) {
        is io.ktor.network.sockets.SocketTimeoutException -> true
        is ServerResponseException -> {
            when ((cause as? ServerResponseException)?.response?.status) {
                HttpStatusCode.BadGateway,
                HttpStatusCode.GatewayTimeout,
                HttpStatusCode.ServiceUnavailable,
                -> true
                else -> false
            }
        }
        else -> false

    }
}
