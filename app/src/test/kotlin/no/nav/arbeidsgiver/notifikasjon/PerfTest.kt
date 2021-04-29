package no.nav.arbeidsgiver.notifikasjon


import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import kotlinx.coroutines.*
import java.lang.System.currentTimeMillis
import java.time.Instant
import kotlin.system.measureTimeMillis

fun main() = runBlocking {
    client.use {
        nyBeskjed(10_000)
        hentNotifikasjoner(10_000)
    }
}

val client = HttpClient(Apache) {
    engine {
        socketTimeout = 0
        connectTimeout = 0
        connectionRequestTimeout = 0
        customizeClient {
            setMaxConnTotal(20)
        }
    }
}
const val selvbetjeningToken =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImZ5akpfczQwN1ZqdnRzT0NZcEItRy1IUTZpYzJUeDNmXy1JT3ZqVEFqLXcifQ.eyJleHAiOjE2MTk2NjAyMTQsIm5iZiI6MTYxOTY1NjYxNCwidmVyIjoiMS4wIiwiaXNzIjoiaHR0cHM6Ly9uYXZ0ZXN0YjJjLmIyY2xvZ2luLmNvbS9kMzhmMjVhYS1lYWI4LTRjNTAtOWYyOC1lYmY5MmMxMjU2ZjIvdjIuMC8iLCJzdWIiOiIxNjEyMDEwMTE4MSIsImF1ZCI6IjAwOTBiNmUxLWZmY2MtNGMzNy1iYzIxLTA0OWY3ZDFmMGZlNSIsImFjciI6IkxldmVsNCIsIm5vbmNlIjoiVFp1Ti1TTi01ZG9zSmVoYkg5SGNaTVJNMUxZcTVHbmNmZTdXdi1ydEhSRSIsImlhdCI6MTYxOTY1NjYxNCwiYXV0aF90aW1lIjoxNjE5NjU2NjEzLCJqdGkiOiJUZTFlN3dzODQxeUtyaFlFTGVFdzZLb3NObDZNRlNfdVFaUGRRa3EzRzlrIiwiYXRfaGFzaCI6IlQ5RHA3M1BzaWlidjhFdVhfMENkVFEifQ.szuNOGqw-Cv0Hh09Hvzvu1n5nD-aYulp7LJhc49dGcQN9m7T_PfIRaHL0h8OXT8Ed8xGoZpRUVs5TvDoNGKc28ZrRmT3acjePD9OUUM-GNmSIZfQWty32PenBAY4dPLlqakbKiu-0MuIg0X8PA6hZD7BMlv-Z0aP4zlGKZC9M9VBV3OKD09dAaLzhLlFseCd5LnEcFagN0ATdkgV6WqEoS3h06UdqaNJqfvx8UzmLCjw8VM5UgLdNUw-tNYHWy7F8m6GkqMTQW9YH1ezTImEZzIrhIe9ZXkhGtBNUrB-y3klx-I3soQuBAO2NnWIpKfAhx0EEjgNNf2OWmdfnFXjRg"
val tokenDingsToken : String = runBlocking {
    client.post<HttpResponse>("https://fakedings.dev-gcp.nais.io/fake/custom") {
        contentType(FormUrlEncoded)
        body = "sub=someproducer&aud=produsent-api"
    }.readText()
}

suspend fun concurrentWithStats(
    title: String = "work",
    times: Int,
    work: suspend () -> Unit
) = coroutineScope {
    val progressBar = ProgressBar(title = title, times)
    val start = currentTimeMillis()
    val stats = (1..times).map {
        async {
            kotlin.runCatching {
                val ms = measureTimeMillis {
                    work.invoke()
                }
                ms
            }.onSuccess {
                progressBar.add()
            }.onFailure {
                println()
                println(it)
                progressBar.add()
            }
        }
    }.awaitAll()
    val durationMs = currentTimeMillis() - start
    val statsSuccess = stats.filter { it.isSuccess }.map { it.getOrThrow() }
    val statsFailure = stats.filter { it.isFailure }
    println(
        """
        |----------------------------
        | $title stats:
        |  Error: ${((statsFailure.count().toDouble() / stats.count()) * 100).toInt()}%
        |  
        |     Duration: ${durationMs}ms 
        |     Ok count: ${statsSuccess.count()}
        |  Error count: ${statsFailure.count()}
        |  Total Count: ${stats.count()}
        |  
        |  Ok stats:
        |       Max: ${statsSuccess.maxOrNull()}ms
        |       Min: ${statsSuccess.minOrNull()}ms
        |   Average: ${statsSuccess.average().toInt()}ms
        |       Sum: ${statsSuccess.sum()}ms
        |     req/s: ${statsSuccess.count() / (durationMs / 1000)}
        |----------------------------
        |""".trimMargin()
    )
}

enum class Api(val url: String) {
    LOCAL("http://localhost:8080/api/graphql"),
    BRUKER_GCP("https://ag-notifikasjon-bruker-api.dev.nav.no/api/graphql"),
    PRODUSENT_GCP("https://ag-notifikasjon-produsent-api.dev.nav.no/api/graphql"),
}

suspend fun hentNotifikasjoner(count: Int, api: Api = Api.BRUKER_GCP) {
    concurrentWithStats("hentNotifikasjoner", count) {
        client.post<HttpResponse>(api.url) {
            headers {
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $selvbetjeningToken")
                if (api == Api.LOCAL) {
                    append(HttpHeaders.Host, "ag-notifikasjon-bruker-api.nav.no")
                }
            }

            body = """{
                    | "query": "{
                    |     notifikasjoner {
                    |         ...on Beskjed {
                    |             lenke
                    |             tekst
                    |             merkelapp
                    |             opprettetTidspunkt
                    |         }
                    |     }
                    | }"
                    |}""".trimMarginAndNewline()
        }
    }
}

suspend fun nyBeskjed(count: Int, api: Api = Api.PRODUSENT_GCP) {
    val run = Instant.now()
    val eksterIder = generateSequence(1) { it + 1 }.iterator()
    concurrentWithStats("nyBeskjed", count) {
        client.post<HttpResponse>(api.url) {
            headers {
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
                if (api == Api.LOCAL) {
                    append(HttpHeaders.Host, "ag-notifikasjon-produsent-api.nav.no")
                }
            }

            body = """{
                    | "query": "mutation {
                    |      nyBeskjed(nyBeskjed: {
                    |          lenke: \"https://min-side-arbeidsgiver.dev.nav.no/min-side-arbeidsgiver/?bedrift=910825631\",
                    |          tekst: \"Du kan nå søke om Lønnstilskudd. Følg lenken for å finne ut mer.\",
                    |          merkelapp: \"tiltak\",
                    |          mottaker: {
                    |              altinn: {
                    |                  altinntjenesteKode: \"5159\",
                    |                  altinntjenesteVersjon: \"1\",
                    |                  virksomhetsnummer: \"910825518\"
                    |              }
                    |          },
                    |          eksternId: \"$run-${eksterIder.next()}\"
                    |      }) {
                    |          id
                    |          errors {
                    |              __typename
                    |              feilmelding
                    |          }
                    |      }
                    | }"
                    |}""".trimMarginAndNewline()
        }
    }
}

class ProgressBar(
    val title: String,
    private val count: Int,
    private val size: Int = 100
) {
    private var curr = 0

    fun add(i: Int = 1) {
        curr += i
        print()
    }

    private fun print() {
        print(progressbar)
        if (percent == 100) {
            println("")
            println("$title complete!")
        }
    }

    private val percent get() = ((curr.toDouble() / count) * 100).toInt()
    private val progress get() = (size.toDouble() * (percent.toDouble() / 100)).toInt()
    private val progressbar: String
        get() {
            val hashes = (0..size).joinToString("") {
                when {
                    it <= progress -> "#"
                    else -> " "
                }
            }
            return "\r$title [$hashes] $percent% ($curr / $count)"
        }
}

fun String.trimMarginAndNewline() =
    this.trimMargin().replace(Regex("\n"), " ")