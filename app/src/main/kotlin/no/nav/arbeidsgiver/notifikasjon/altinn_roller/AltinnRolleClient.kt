package no.nav.arbeidsgiver.notifikasjon.altinn_roller

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnConfig
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

interface AltinnRolleClient {
    suspend fun hentRoller(): List<AltinnRolle>?
}

class AltinnRolleClientImpl: AltinnRolleClient {
    private val log = logger()

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    private val hentRollerUrl = "${AltinnConfig.ALTINN_ORIGIN}/ekstern/altinn/api/serviceowner/roledefinitions?ForceEIAuthentication&language=1044"

    override suspend fun hentRoller(): List<AltinnRolle>? = try {
        httpClient.get(hentRollerUrl) {
            headers {
                append("X-NAV-APIKEY", AltinnConfig.GW_KEY)
                append("APIKEY", AltinnConfig.ALTINN_KEY)
            }
        }
    } catch (e: ResponseException) {
        log.warn("serviceowner/roledefinitions feiler: ${e.response.status.value} '${e.response.status.description}'", e)
        null
    } catch (e: Exception) {
        log.warn("serviceowner/roledefinitions feiler: : '${e.message}' ", e)
        null
    }
}