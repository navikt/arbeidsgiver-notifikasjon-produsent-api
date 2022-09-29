package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXPlugin
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.blockingIO


class SuspendingAltinnClient(
    private val blockingClient: AltinnrettigheterProxyKlient = AltinnrettigheterProxyKlient(AltinnConfig.config),
    private val observer: (AltinnReportee) -> Unit,
    private val tokenXClient: TokenXClient = TokenXClientImpl(),
) {

    private val log = logger()
    private val altinnProxyAudience = "${NaisEnvironment.clusterName}:arbeidsgiver:altinn-rettigheter-proxy"

    private val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson()
        }
        install(PropagateFromMDCPlugin) {
            propagate("x_correlation_id")
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
        }
        install(TokenXPlugin) {
            audience = altinnProxyAudience
            tokenXClient = this@SuspendingAltinnClient.tokenXClient
        }
    }

    suspend fun hentOrganisasjoner(
        selvbetjeningToken: Token,
        subject: Subject,
        serviceCode: ServiceCode,
        serviceEdition: ServiceEdition,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee>? =
        withErrorHandler {
            val accessToken = tokenXClient.exchange(selvbetjeningToken.value, altinnProxyAudience)
            blockingIO {
                blockingClient.hentOrganisasjoner(
                    TokenXToken(accessToken),
                    subject,
                    serviceCode,
                    serviceEdition,
                    filtrerPåAktiveOrganisasjoner
                )
            }
        }
            ?.onEach(observer)

    suspend fun hentOrganisasjoner(
        selvbetjeningToken: Token,
        subject: Subject,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee>? =
        withErrorHandler {
            val accessToken = tokenXClient.exchange(selvbetjeningToken.value, altinnProxyAudience)
            blockingIO {
                blockingClient.hentOrganisasjoner(
                    TokenXToken(accessToken),
                    subject,
                    filtrerPåAktiveOrganisasjoner
                )
            }
        }
            ?.onEach(observer)

    private suspend fun <T> withErrorHandler(body: suspend () -> List<T>): List<T>? =
        try {
            body()
        } catch (error: Exception) {
            null.also {
                logException(error)
            }
        }

    suspend fun hentReportees(
        roleDefinitionId: String,
        selvbetjeningsToken: String,
    ): List<AltinnReportee>? {
        // TODO: ta i bruk proxy-klient når vi får utvidet den
        val baseUrl = "http://altinn-rettigheter-proxy.arbeidsgiver/altinn-rettigheter-proxy/ekstern/altinn"
        return try {
            httpClient.get("${baseUrl}/api/serviceowner/reportees?ForceEIAuthentication&roleDefinitionId=$roleDefinitionId") {
                headers {
                    append("Authorization", "Bearer $selvbetjeningsToken")
                    append("APIKEY", System.getenv("ALTINN_HEADER") ?: "default")
                }
            }
                .body()
        } catch (e: Exception) {
            logException(e)
            null
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

    private fun AltinnrettigheterProxyKlientFallbackException.erDriftsforstyrrelse(): Boolean {
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
}
