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

    suspend fun hentAnsatte(token: String): List<NærmesteLederFor>
}

class NærmesteLederServiceImpl(
    private val url: String = System.getenv("NARMESTELEDER_API_ENDPOINT")
) : NærmesteLederService {
    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Ansatte(
        val fnr: String,
        val orgnummer: String
    )

    override suspend fun hentAnsatte(token: String): List<NærmesteLederService.NærmesteLederFor> {
        return httpClient.get<List<Ansatte>>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.map {
            NærmesteLederService.NærmesteLederFor(
                ansattFnr = it.fnr,
                virksomhetsnummer = it.orgnummer, /* Team sykmelding har bekreftet at orgnummer alltid er til underenhet. */
            )
        }
    }
}