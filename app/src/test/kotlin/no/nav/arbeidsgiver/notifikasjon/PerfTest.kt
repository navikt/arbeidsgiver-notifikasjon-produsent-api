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
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImZ5akpfczQwN1ZqdnRzT0NZcEItRy1IUTZpYzJUeDNmXy1JT3ZqVEFqLXcifQ.eyJleHAiOjE2MTk0NTIzNzcsIm5iZiI6MTYxOTQ0ODc3NywidmVyIjoiMS4wIiwiaXNzIjoiaHR0cHM6Ly9uYXZ0ZXN0YjJjLmIyY2xvZ2luLmNvbS9kMzhmMjVhYS1lYWI4LTRjNTAtOWYyOC1lYmY5MmMxMjU2ZjIvdjIuMC8iLCJzdWIiOiIxNjEyMDEwMTE4MSIsImF1ZCI6IjAwOTBiNmUxLWZmY2MtNGMzNy1iYzIxLTA0OWY3ZDFmMGZlNSIsImFjciI6IkxldmVsNCIsIm5vbmNlIjoiYURFc3lpcjJuYUtHaHF3Rm5FWkRMSF9yLXcyTWh1N3ZuVEppVnNMTFNRayIsImlhdCI6MTYxOTQ0ODc3NywiYXV0aF90aW1lIjoxNjE5NDQ4Nzc2LCJqdGkiOiJZazU5aERvY1NhcE9fdjJtWVNXQW00VS1Dazg1cm91MnR3TFZMR3doTlVzIiwiYXRfaGFzaCI6IkU0VkJPSGtzdkM4RTB4Nm1BMW03RWcifQ.SOuaammouGocprnkBUHC7dKeu5mOFvuhdYUi3yONhIv2x0KmMmhfkaVV2HOaGpMzvcnwlaGMyUFIdBmbJMa6VwbTsaNNveXorwFMPqM0o-qEBe44KPXeUolSMaLojSXKnTYEYe8xV-AJFMJtwTaJ86orDiRPre0r_nGU8RXXSvzQmxF6nnySoduaqTTTEINhnnikfR419Lxsx-fCzMlorsUUPnktM4ItwruwtqIdckmQ2h96kDk5mQCYk5iQO6F4RHaau2aSrT0zsPMZx7psS33dlbmeY3klqdXPEBF28zYAm_M9gLpgHAzWtW_ZKPIPYUT43DTE0hxGdIHS5qNeLQ"
const val tokenDingsToken =
    "eyJraWQiOiJtb2NrLW9hdXRoMi1zZXJ2ZXIta2V5IiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJzb21lcHJvZHVjZXIiLCJhdWQiOiJwcm9kdXNlbnQtYXBpIiwibmJmIjoxNjE5NDQ4ODEyLCJpc3MiOiJodHRwczpcL1wvZmFrZWRpbmdzLmRldi1nY3AubmFpcy5pb1wvZmFrZSIsImV4cCI6MTYyMzA0ODgxMiwiaWF0IjoxNjE5NDQ4ODEyLCJqdGkiOiI4Y2RiODNiYi02NjcxLTQ0MGMtYTI1Ni04NDI4M2IwNzBkMzkifQ.WmR0WQXRZARLvsaCmBSh9RPjRuzhAzNwv5eEaEz7uzxHorpIvklA0dTtI_XLtKDZBfn6eaAZKuTR8-caOvmd2rA9Hcpld_BLZo19R_0iw3L8ZT_MQv56UBT9ZWOLTgg4lw0zC0D88gZd6j_mb2t-_ESybmci9Slz603qwdujN08RIa_zfgFz8_ibvp8RTQ9PQbxR_RGSpHpMgQ4kHft2qgBvFvp9gNkeJk8gZ4gHfi23VjJD8vXl2PWbhxNpC520fNRETvoZeR0z5caQeEYGoGm0viwUC0fovd3V_68IUT3Vf99O9tXhrJDPXlsT1VAc3Hj1Mp8QJb7IVMwOiQNbRA"

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
    nyBeskjed(1, Api.PRODUSENT_GCP)
    hentNotifikasjoner(1, Api.BRUKER_GCP)
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