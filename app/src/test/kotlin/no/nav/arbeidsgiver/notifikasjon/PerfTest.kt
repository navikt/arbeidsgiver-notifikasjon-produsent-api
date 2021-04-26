package no.nav.arbeidsgiver.notifikasjon


import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.lang.System.currentTimeMillis
import java.time.Instant
import kotlin.system.measureTimeMillis

val client = HttpClient(Apache) {
    engine {
        followRedirects = true
        socketTimeout = 10_000
        connectTimeout = 10_000
        connectionRequestTimeout = 20_000
        customizeClient {
            setMaxConnTotal(1000)
            setMaxConnPerRoute(100)
        }
    }
}
const val selvbetjeningToken =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImZ5akpfczQwN1ZqdnRzT0NZcEItRy1IUTZpYzJUeDNmXy1JT3ZqVEFqLXcifQ.eyJleHAiOjE2MTk0NDE5MzIsIm5iZiI6MTYxOTQzODMzMiwidmVyIjoiMS4wIiwiaXNzIjoiaHR0cHM6Ly9uYXZ0ZXN0YjJjLmIyY2xvZ2luLmNvbS9kMzhmMjVhYS1lYWI4LTRjNTAtOWYyOC1lYmY5MmMxMjU2ZjIvdjIuMC8iLCJzdWIiOiIxNjEyMDEwMTE4MSIsImF1ZCI6IjAwOTBiNmUxLWZmY2MtNGMzNy1iYzIxLTA0OWY3ZDFmMGZlNSIsImFjciI6IkxldmVsNCIsIm5vbmNlIjoiR1IxWnR6eTlnZlNrTlB5WHc5ZWVLc0FmYUlNUVFWa1Etckc5eDIyZTZ6byIsImlhdCI6MTYxOTQzODMzMiwiYXV0aF90aW1lIjoxNjE5NDM4MzMxLCJqdGkiOiJJT3k2clVMallmZnR3TTk0bUJ0T2lRTjM4SnJSZ05OdmlMSkl1blFVelM0IiwiYXRfaGFzaCI6IkxHVnZVY211SkMwQ1NHeU1pamxUd2cifQ.oljrI1o3xOxOJFNPw0wPQcZDN77ZerHbiCm4wIfQglhhnjWxA9q3HKkeskmw_CczlyA3FfCCJaXPjKqoXINRkUP1yz9qV3ZVmmEYLi_Q6dqBK9q7A7bLpz9oaeqEhN3iGkO5l9UN4maOdo0fCcgDX_7vtjHtd0nihr7iWJh975PnY6Y88h28_l6PPgPGYjMLABDOX1I-y0BoCcRUCnbMOtUOwdCpKj-mF4QeZzpB1_RFewn1lzVuCcNoHyvOjhhTzDcwFBfyAC4LncIaWiSvaygWav8ZR9ArcQSCqpC_mUN75pvTJQ4wfw5_b_45m9JeEvPbIL559l98kG8fxjB6SA"
const val tokenDingsToken =
    "eyJraWQiOiJtb2NrLW9hdXRoMi1zZXJ2ZXIta2V5IiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJzb21lcHJvZHVjZXIiLCJhdWQiOiJwcm9kdXNlbnQtYXBpIiwibmJmIjoxNjE5NDM4Mzc3LCJpc3MiOiJodHRwczpcL1wvZmFrZWRpbmdzLmRldi1nY3AubmFpcy5pb1wvZmFrZSIsImV4cCI6MTYyMzAzODM3NywiaWF0IjoxNjE5NDM4Mzc3LCJqdGkiOiI1YTc1NDA0OS1mOWJhLTRhZTktYjgxOS00Nzc4NzcxMTZiNmYifQ.ij9Og7rt-s-StGpc3-jMid-SocXRLLbKosg9Nzx9j28okhGHrVrFM6J8SzL9P832HA7N9VPrbdxaVnfZwyjg84c2uO_k7MaKQsTTFx-naXbAqQELpUy6sJGXYBZVF0-J1w_HpSx8amMg2uGDGX3T40iDSM0TVSb2cyxHyNewRCAl6H9MtyurUYqK26QY_ZIw31fGp9YqN11ezsiItQFBTpFxiZz_hLoU_Rf40KoZsc6IVwXzpZBpIfQUAwgzIKt5Gs6in9TIp14fgmVmayye4Tgn2Yfl20ZRk8M8DkWNGdyHJ3X9DjJQjVEE6iqGAdgkpcRA7t7LQil_e6ITG9DNSA"

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
                println(it)
                progressBar.add()
            }
        }
    }.awaitAll()
    val statsSuccess = stats.filter { it.isSuccess }.map { it.getOrThrow() }
    val statsFailure = stats.filter { it.isFailure }
    println(
        """
        |----------------------------
        | $title stats:
        |  Error: ${((statsFailure.count().toDouble() / stats.count()) * 100).toInt()}%
        |  
        |     Duration: ${currentTimeMillis() - start}ms 
        |     Ok count: ${statsSuccess.count()}
        |  Error count: ${statsFailure.count()}
        |  Total Count: ${stats.count()}
        |  
        |  Ok stats:
        |       Max: ${statsSuccess.maxOrNull()}ms
        |       Min: ${statsSuccess.minOrNull()}ms
        |   Average: ${statsSuccess.average().toInt()}ms
        |       Sum: ${statsSuccess.sum()}ms
        |----------------------------
        |""".trimMargin()
    )
}

enum class Api(val url: String) {
    LOCAL("http://localhost:8080/api/graphql"),
    BRUKER_GCP("https://ag-notifikasjon-bruker-api.dev.nav.no/api/graphql"),
    PRODUSENT_GCP("https://ag-notifikasjon-produsent-api.dev.internal.nav.no/api/graphql"),
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
    nyBeskjed(1, Api.LOCAL)
    hentNotifikasjoner(1, Api.LOCAL)
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