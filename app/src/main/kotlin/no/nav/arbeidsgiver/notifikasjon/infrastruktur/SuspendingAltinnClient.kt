package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.blockingIO

class SuspendingAltinnClient(
    private val blockingClient: AltinnrettigheterProxyKlient = AltinnrettigheterProxyKlient(AltinnConfig.config),
    private val observer: (AltinnReportee) -> Unit,
) {
    private val log = logger()

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        install(PropagateFromMDCFeature) {
            propagate("x_correlation_id")
        }
        install(HttpClientMetricsFeature)
    }

    suspend fun hentOrganisasjoner(
        selvbetjeningToken: SelvbetjeningToken,
        subject: Subject,
        serviceCode: ServiceCode,
        serviceEdition: ServiceEdition,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee>? =
        withErrorHandler {
            blockingIO {
                blockingClient.hentOrganisasjoner(
                    selvbetjeningToken,
                    subject,
                    serviceCode,
                    serviceEdition,
                    filtrerPåAktiveOrganisasjoner
                )
            }
        }
            ?.onEach(observer)

    suspend fun hentOrganisasjoner(
        selvbetjeningToken: SelvbetjeningToken,
        subject: Subject,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee>? =
        withErrorHandler {
            blockingIO {
                blockingClient.hentOrganisasjoner(
                    selvbetjeningToken,
                    subject,
                    filtrerPåAktiveOrganisasjoner
                )
            }
        }
            ?.onEach(observer)

    private suspend fun <T> withErrorHandler(body: suspend () -> List<T>): List<T>? =
        try {
            body()
        } catch (error: AltinnException) {
            when (error.proxyError.httpStatus) {
                400, 403 -> listOf()
                else -> null.also {
                    logException(error)
                }
            }
        } catch (error: Exception) {
            if (error.message?.contains("403") == true)
                listOf()
            else
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
            httpClient.get<List<AltinnReportee>>("${baseUrl}/api/serviceowner/reportees?ForceEIAuthentication&roleDefinitionId=$roleDefinitionId") {
                headers {
                    append("Authorization", "Bearer $selvbetjeningsToken")
                    append("APIKEY", System.getenv("ALTINN_HEADER") ?: "default")
                }
            }
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
