package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*

interface NærmesteLederService {
    data class NærmesteLederFor(
        val ansattFnr: String,
        val virksomhetsnummer: String,
    )

    suspend fun hentAnsatte(userToken: String): List<NærmesteLederFor>
}

class NærmesteLederServiceImpl(
    private val tokenExchangeClient: TokenExchangeClient = TokenExchangeClientImpl(),
    baseUrl: String = basedOnEnv(
        prod = "https://narmesteleder.nav.no",
        other = "https://narmesteleder.dev.nav.no",
    )
) : NærmesteLederService {
    private val log = logger()

    private val targetAudience = basedOnEnv(
        prod = "prod-gcp:teamsykmelding:narmesteleder",
        other = "dev-gcp:teamsykmelding:narmesteleder"
    )

    private val url = URLBuilder()
        .takeFrom(baseUrl)
        .pathComponents("arbeidsgiver", "v2", "ansatte")
        .build()

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Ansatte(
        val ansatte: List<Ansatt>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Ansatt(
        val fnr: String,
        val orgnummer: String
    )

    override suspend fun hentAnsatte(userToken: String): List<NærmesteLederService.NærmesteLederFor> {
        val onBehalfToken = tokenExchangeClient.exchangeToken(userToken, targetAudience)

        return httpClient.get<Ansatte>(url) {
            header(HttpHeaders.Authorization, "Bearer $onBehalfToken")
        }.ansatte.map {
            NærmesteLederService.NærmesteLederFor(
                ansattFnr = it.fnr,
                virksomhetsnummer = it.orgnummer, /* Team sykmelding har bekreftet at orgnummer alltid er til underenhet. */
            )
        }
    }
}