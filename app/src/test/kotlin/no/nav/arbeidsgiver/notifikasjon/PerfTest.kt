package no.nav.arbeidsgiver.notifikasjon


import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.lang.System.currentTimeMillis
import java.time.Instant
import kotlin.system.measureTimeMillis

val client = HttpClient(Apache) {
    engine {
        socketTimeout = 0
        connectTimeout = 0
        connectionRequestTimeout = 0
        customizeClient {
            setMaxConnTotal(10)
        }
    }
}
const val selvbetjeningToken =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImZ5akpfczQwN1ZqdnRzT0NZcEItRy1IUTZpYzJUeDNmXy1JT3ZqVEFqLXcifQ.eyJleHAiOjE2MTk0NzAzNDIsIm5iZiI6MTYxOTQ2Njc0MiwidmVyIjoiMS4wIiwiaXNzIjoiaHR0cHM6Ly9uYXZ0ZXN0YjJjLmIyY2xvZ2luLmNvbS9kMzhmMjVhYS1lYWI4LTRjNTAtOWYyOC1lYmY5MmMxMjU2ZjIvdjIuMC8iLCJzdWIiOiIxNjEyMDEwMTE4MSIsImF1ZCI6IjAwOTBiNmUxLWZmY2MtNGMzNy1iYzIxLTA0OWY3ZDFmMGZlNSIsImFjciI6IkxldmVsNCIsIm5vbmNlIjoiNDdvWmNGU045YmxxNkVJOHROLWE2aThVNUVHZzBWR281ZVF3SENkZVAzcyIsImlhdCI6MTYxOTQ2Njc0MiwiYXV0aF90aW1lIjoxNjE5NDY2NzQxLCJqdGkiOiIwRXNDaFBubkQ3OFYzZ3Q1cF9nTmdqWTNGTUx4NmtMV21iMGFMQ3JCTWFrIiwiYXRfaGFzaCI6ImFZZmxsU2NmNm1ReDN1NF9MYzdjZ2cifQ.MAAQniUjWasxIi28DGl4j6WE63Tai4oo5YUWX-emmZHseOsMs6zbzMBh1rovsfddF66ZxJUPXty8oGvzIKCy7xyz_P7J1DZdL8NQp6GlOp1StE6OLOYWQh-BPCc4etoZs3ZZ9E44zudQDp2EmuOZYMkGBIWhRxIQdUqWXUV-TFRo5QyqdNy2CPCvVKjtLFYY9vSXQigcCzeDVijpKqTJb8679ZMhob0KBXGeiRqLQnvgPeRMilqeagu77QDAe9bAYxeY6mQUpQ8KPWjIQTNA3wj2hRX9XVA0tnAvjftovM8tO7prVOGwf1a6AqF135Te35Q1f8422hdmBtm3h6n3MA"
const val tokenDingsToken =
    "eyJraWQiOiJtb2NrLW9hdXRoMi1zZXJ2ZXIta2V5IiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJzb21lcHJvZHVjZXIiLCJhdWQiOiJwcm9kdXNlbnQtYXBpIiwibmJmIjoxNjE5NDY2ODA0LCJpc3MiOiJodHRwczpcL1wvZmFrZWRpbmdzLmRldi1nY3AubmFpcy5pb1wvZmFrZSIsImV4cCI6MTYyMzA2NjgwNCwiaWF0IjoxNjE5NDY2ODA0LCJqdGkiOiJkNjM4NzA1OC0wYzE2LTRmOWUtYmVlYy1kYjg1NzJlNDZlYzEifQ.FhMOh9nwW9rGLKBFCu0SyMug_JV_6bRYHaoAQU9vPLyp1uYuBWjObZRb61ZxYjtLWjm3IW45P-JIuxGDNF3cxb9lnDD9ayc_Yzox9I8s7vgChL1-3yZN_jDHS2hPvgNFgJSf2eb7nNx4jsk2f-1YCArfD1eLYGOzLxPLbhaKRP4w-srxuvcwL826lYxH8ojQQ8c-V03QFMBI54__WGcGOZn-VeuR2YNJbdcvhu9CCZdFh0-ZystDtOsWyyqqN01H0RFIybNhr8FLcN2mvCF1Njb8iUSrLpdDZR7i9FNHMPsHmhB0SZg532xrxXQyH56zRUnYmlJO9laoF99kcSMDTw"

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

fun main() = runBlocking {
    client.use {
        nyBeskjed(1_000, Api.PRODUSENT_GCP)
        hentNotifikasjoner(1_000, Api.BRUKER_GCP)
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