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
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger.Companion.flatten
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
        roller: Iterable<AltinnRolle>,
    ): Tilganger

    suspend fun hentRoller(): List<AltinnRolle>
}

val nonBlockingAltinnrettigheterProxyKlient = NonBlockingAltinnrettigheterProxyKlient(
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

class AltinnImpl(
    private val klient: NonBlockingAltinnrettigheterProxyKlient = nonBlockingAltinnrettigheterProxyKlient
) : Altinn {
    private val log = logger()

    private val timer = Health.meterRegistry.timer("altinn_klient_hent_alle_tilganger")

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    private fun logException(e: Exception) {
        if (e is AltinnrettigheterProxyKlientFallbackException) {
            if (e.erDriftsforstyrrelse())
                log.info("Henting av Altinn-tilganger feilet", e)
            else
                log.error("Henting av Altinn-tilganger feilet", e)
        } else {
            log.error("Henting av Altinn-tilganger feilet", e)
        }
    }

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>,
        roller: Iterable<AltinnRolle>,
    ): Tilganger =
        timer.coRecord {
            coroutineScope {
                val tjenesteTilganger = tjenester.map {
                    val (code, version) = it
                    async {
                        hentTilganger(fnr, code, version, selvbetjeningsToken)
                    }
                }
                val rolleTilganger = roller.map {
                    val (RoleDefinitionId, RoleDefinitionCode) = it
                    async {
                        hentTilgangerForRolle(RoleDefinitionId, RoleDefinitionCode, selvbetjeningsToken)
                    }
                }
                val reporteeTilganger = async {
                    hentTilganger(fnr, selvbetjeningsToken)
                }
                return@coroutineScope tjenesteTilganger.awaitAll().flatten() + reporteeTilganger.await() + rolleTilganger.awaitAll().flatten()
            }
        }

    private suspend fun hentTilganger(
        fnr: String,
        serviceCode: String,
        serviceEdition: String,
        selvbetjeningsToken: String,
    ): Tilganger {
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
                400, 403 -> return Tilganger()
                else -> throw error
            }
        } catch (error: Exception) {
            if (error.message?.contains("403") == true)
                return Tilganger()
            else
                throw error
        }

        return Tilganger(reporteeList
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
        )
    }

    private suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
    ): Tilganger {
        val reporteeList = try {
            klient.hentOrganisasjoner(
                SelvbetjeningToken(selvbetjeningsToken),
                Subject(fnr),
                true
            )
        } catch (error: AltinnException) {
            return when (error.proxyError.httpStatus) {
                403 -> Tilganger()
                else -> Tilganger.FAILURE.also{ logException(error) }
            }
        } catch (error: Exception) {
            return if (error.message?.contains("403") == true)
                Tilganger()
            else
                Tilganger.FAILURE.also{ logException(error) }
        }

        return Tilganger(reportee = reporteeList.map {
            BrukerModel.Tilgang.AltinnReportee(
                virksomhet = it.organizationNumber!!,
                fnr = fnr
            )
        }
        )
    }

    private suspend fun hentTilgangerForRolle(
        roleDefinitionId: String,
        roleDefinitionCode: String,
        selvbetjeningsToken: String,
    ): Tilganger {
        // TODO: ta i bruk proxy-klient når vi får utvidet den
        val baseUrl = "http://altinn-rettigheter-proxy.arbeidsgiver/altinn-rettigheter-proxy/ekstern/altinn"

        try {
            val reportees =
                httpClient.get<List<AltinnReportee>>("${baseUrl}/api/serviceowner/reportees?ForceEIAuthentication&roleDefinitionId=$roleDefinitionId") {
                    headers {
                        append("Authorization", "Bearer $selvbetjeningsToken")
                        append("APIKEY", System.getenv("ALTINN_HEADER") ?: "default")
                    }
                }

            return Tilganger(rolle = reportees.map {
                BrukerModel.Tilgang.AltinnRolle(
                    virksomhet = it.organizationNumber!!,
                    roleDefinitionId = roleDefinitionId,
                    roleDefinitionCode = roleDefinitionCode
                )
            })
        } catch (e: Exception) {
            logException(e)
            return Tilganger.FAILURE
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
