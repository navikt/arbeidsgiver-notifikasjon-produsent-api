package no.nav.arbeidsgiver.notifikasjon.altinn_roller

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*

interface AltinnRolleClient {
    suspend fun hentRoller(): List<AltinnRolle>?
}

class AltinnRolleClientImpl: AltinnRolleClient {
    private val log = logger()

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
    }

    private val hentRollerUrl = "${AltinnConfig.ALTINN_ORIGIN}/ekstern/altinn/api/serviceowner/roledefinitions?ForceEIAuthentication&language=1044"

    override suspend fun hentRoller(): List<AltinnRolle>? = try {
        httpClient.get(hentRollerUrl) {
            headers {
                append("X-NAV-APIKEY", AltinnConfig.GW_KEY)
                append("APIKEY", AltinnConfig.ALTINN_KEY)
            }
        }.body()
    } catch (e: ResponseException) {
        log.warn("serviceowner/roledefinitions feiler: ${e.response.status.value} '${e.response.status.description}'", e)
        null
    } catch (e: Exception) {
        log.warn("serviceowner/roledefinitions feiler: : '${e.message}' ", e)
        null
    }
}