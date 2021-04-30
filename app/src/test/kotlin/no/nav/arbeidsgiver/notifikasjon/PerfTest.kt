package no.nav.arbeidsgiver.notifikasjon


import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.VÅRE_TJENESTER
import java.lang.System.currentTimeMillis
import java.time.Instant
import kotlin.system.measureTimeMillis

fun main() = runBlocking {
    client.use {
        nyBeskjed(100_000, Api.LOCAL)
//        hentNotifikasjoner(10_000, Api.LOCAL)
    }
}
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
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImZ5akpfczQwN1ZqdnRzT0NZcEItRy1IUTZpYzJUeDNmXy1JT3ZqVEFqLXcifQ.eyJleHAiOjE2MTk3ODgyMzksIm5iZiI6MTYxOTc4NDYzOSwidmVyIjoiMS4wIiwiaXNzIjoiaHR0cHM6Ly9uYXZ0ZXN0YjJjLmIyY2xvZ2luLmNvbS9kMzhmMjVhYS1lYWI4LTRjNTAtOWYyOC1lYmY5MmMxMjU2ZjIvdjIuMC8iLCJzdWIiOiIxNjEyMDEwMTE4MSIsImF1ZCI6IjAwOTBiNmUxLWZmY2MtNGMzNy1iYzIxLTA0OWY3ZDFmMGZlNSIsImFjciI6IkxldmVsNCIsIm5vbmNlIjoiU09vWjJzRWRZUW45dVh1MXo5emVtblRuZThDcU9sMEVFM1hpdnhkY3dYTSIsImlhdCI6MTYxOTc4NDYzOSwiYXV0aF90aW1lIjoxNjE5Nzg0NjM5LCJqdGkiOiJQVTBWYWNYX2pUZ1VkSTlJaTZIRXR3ZGtZcXBrRnNiWmtKX09nX3ZidlA0IiwiYXRfaGFzaCI6IjFJQXE1WDBEeWZ3X2ZvaExzNTJESUEifQ.PsVGg9M4PR0ovhPlxpt-PP1NXEI4bucvIHic5hEx1_cM-mrLWTTVcvyG7xwVWE3fuGzeVOGFf7QDX1d338cwu21_BEKgDBMb-oUStGO_1QjJPsFdmkeS1sWnapVLEzaVhZm1b-dX2iXamK-XyR4jouUn5JIQJl6mANc6qCyrBlQVNfcwwePujXCpx3ZCcYHvzWMgYOXzPGJq-IAfmd835g1lb2uB2mxnbHUAkPMVe000pHvbr4bv0L-an54kmBN9iWS4Yz4zgneej9L-y4zbfrItCkDS7YYL8_n4QmfT5wU0fXb-_y7JlW53aBvYU_bfvtDrSkhr7IohGh9nkzzr9Q"
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
    val tjenester = VÅRE_TJENESTER.asSequence().looping().iterator()
    concurrentWithStats("nyBeskjed", count) {
        val (tjenesteKode, tjenesteVersjon) = tjenester.next()
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
                    |                  altinntjenesteKode: \"$tjenesteKode\",
                    |                  altinntjenesteVersjon: \"$tjenesteVersjon\",
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

fun <T> Sequence<T>.looping() = generateSequence(this) { it }.flatten()