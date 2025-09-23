package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.defaultHttpClient

interface Enhetsregisteret {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Underenhet(
        val organisasjonsnummer: String,
        val navn: String,
    )

    suspend fun hentUnderenhet(orgnr: String): Underenhet
}

fun enhetsregisterFactory() =
    basedOnEnv(
        prod = { EnhetsregisteretImpl() },
        dev = { EnhetsregisteretImpl() },
        other = { EnhetsregisteretDevImpl() }
    )

class EnhetsregisteretDevImpl : Enhetsregisteret {
    override suspend fun hentUnderenhet(orgnr: String) =
        Enhetsregisteret.Underenhet(
            organisasjonsnummer = orgnr,
            navn = ""
        )
}

class EnhetsregisteretImpl(
    val httpClient: HttpClient = defaultHttpClient {
        expectSuccess = false
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            staticPath = "/enhetsregisteret/api/underenheter/"
        }
    },
) : Enhetsregisteret {
    private val log = logger()

    private val timer = Metrics.meterRegistry.timer("brreg_hent_organisasjon")

    private val baseUrl: String = basedOnEnv(
        prod = { "https://ereg-services.prod-fss-pub.nais.io" },
        other = { "https://ereg-services-q1.dev-fss-pub.nais.io" },
    )

    override suspend fun hentUnderenhet(orgnr: String) = timer.coRecord {
        val response: HttpResponse = try {
            httpClient.get("$baseUrl/v1/organisasjon/$orgnr/noekkelinfo")
        } catch (e: RuntimeException) {
            e.rethrowIfCancellation()
            log.warn("kall mot $baseUrl feilet", e)
            return@coRecord Enhetsregisteret.Underenhet(orgnr, "")
        }
        if (response.status.isSuccess()) {
            try {
                Enhetsregisteret.Underenhet(
                    organisasjonsnummer = orgnr,
                    navn = response.body<NavEregResponse>().navn.navn
                )
            } catch (e: RuntimeException) {
                e.rethrowIfCancellation()
                log.warn("feil ved deserializing av response fra enhetsregisteret", e)
                Enhetsregisteret.Underenhet(orgnr, "")
            }
        } else {
            log.warn("kunne ikke finne navn for virksomhet. kall til brreg feilet: ${response.status} ${response.bodyAsText()}")
            Enhetsregisteret.Underenhet(orgnr, "")
        }
    }

    // https://ereg-services.dev.intern.nav.no/swagger-ui/index.html#/organisasjon.v1/hentNoekkelinfoOrganisasjonUsingGET
    @JsonIgnoreProperties(ignoreUnknown = true)
    private class NavEregResponse(
        val navn: Navn,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        class Navn(
            val navnelinje1: String? = null,
            val navnelinje2: String? = null,
            val navnelinje3: String? = null,
            val navnelinje4: String? = null,
            val navnelinje5: String? = null,
        ) {
            val navn: String
                get() =
                    listOfNotNull(navnelinje1, navnelinje2, navnelinje3, navnelinje4, navnelinje5)
                        .filter { it.isNotBlank() }
                        .joinToString(separator=" ")
        }
    }
}
