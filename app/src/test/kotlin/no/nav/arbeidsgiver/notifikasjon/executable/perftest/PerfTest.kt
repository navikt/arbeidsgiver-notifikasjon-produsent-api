@file:Suppress("EXPERIMENTAL_API_USAGE_FUTURE_ERROR")

package no.nav.arbeidsgiver.notifikasjon.executable.perftest


import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.FAGER_TESTPRODUSENT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon
import java.lang.System.currentTimeMillis
import java.time.Instant
import java.util.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis


fun main() = runBlocking {
    client.use {
        //nyBeskjed(1, Api.PRODUSENT_LOCAL)
        //nySak(10, Api.PRODUSENT_GCP)
        //nyOppgave(10, Api.PRODUSENT_GCP)
        nyeSakerMedOppgaver(500, Api.PRODUSENT_GCP, it, 400)// Oppretter mange saker med noen ugyldige merkelapper.
        //hentNotifikasjoner(1, Api.BRUKER_GCP)
    }
}

val client = HttpClient(Apache) {
    install(ContentNegotiation) {
        jackson()
    }
    engine {
        socketTimeout = 0
        connectTimeout = 0
        connectionRequestTimeout = 0

        customizeClient {
            setMaxConnTotal(250)
        }
    }
}
const val selvbetjeningToken =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImZ5akpfczQwN1ZqdnRzT0NZcEItRy1IUTZpYzJUeDNmXy1JT3ZqVEFqLXcifQ.eyJleHAiOjE2MjQ0NTIwMTgsIm5iZiI6MTYyNDQ0ODQxOCwidmVyIjoiMS4wIiwiaXNzIjoiaHR0cHM6Ly9uYXZ0ZXN0YjJjLmIyY2xvZ2luLmNvbS9kMzhmMjVhYS1lYWI4LTRjNTAtOWYyOC1lYmY5MmMxMjU2ZjIvdjIuMC8iLCJzdWIiOiIxNjEyMDEwMTE4MSIsImF1ZCI6IjAwOTBiNmUxLWZmY2MtNGMzNy1iYzIxLTA0OWY3ZDFmMGZlNSIsImFjciI6IkxldmVsNCIsIm5vbmNlIjoiTDlCaVlsMlNYVGVVNm1zckpuZUFwN05DUk9qcFZKQ09QRVNZWk9fQ2IwcyIsImlhdCI6MTYyNDQ0ODQxOCwiYXV0aF90aW1lIjoxNjI0NDQ4NDE3LCJqdGkiOiJSQ3praU1fQmhaajZtRVFvTWk1YjBMV1d6Z3hlSTFWVzkzRExaZGctOUpnIiwiYXRfaGFzaCI6IlRVN24xNnBVVElRR0ZVdUgzdUE5a1EifQ.cVxMow7vGKi_w4jsxtr3XVDfz9i1uQw7fH6KdRSU4R8ahtONaGiiBstXw6Ega1PLJ2NPN1bb5p2Bi9BsdTiXxbtjLIquS1pHC3UgQOeEBx3YOdgfjiPOXWjXYqGciIBWbzjjKRvGkYysICPudrL0YDVMmsPIY90-KclQRbaoSPMnRosmwoF_VEK5PJ8EWpePTzDMwD-W2oR2yQ-0SaX8d4K8Rqzwrz-1aKU4hPVgBYxd4eLapdDS39xEU-l1v3j8zCXp67Vvmq6XJdat6H9IJ9_Ok7fU9E62zjbHliMETe3If53aS8-YXJevI0S1qxH4wROJud0d1595U5yqDFEhjA"

val tokenDingsToken: String = runBlocking {
    client.post("https://fakedings.dev-gcp.nais.io/fake/custom") {
        contentType(FormUrlEncoded)
        setBody(
            "azp=dev-gcp:fager:notifikasjon-test-produsent\n" +
                    "\n&aud=produsent-api"
        )
    }
        .bodyAsText()
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
            val jitter = Random.nextLong(0, 2000)
            delay(jitter)
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
    BRUKER_LOCAL("http://localhost:8082/api/graphql"),
    PRODUSENT_LOCAL("http://localhost:8081/api/graphql"),
    BRUKER_GCP("https://ag-notifikasjon-bruker-api.dev.nav.no/api/graphql"),
    PRODUSENT_GCP("https://ag-notifikasjon-produsent-api.dev.nav.no/api/graphql"),
}


suspend fun hentNotifikasjoner(count: Int, api: Api = Api.BRUKER_GCP) {
    concurrentWithStats("hentNotifikasjoner", count) {
        client.post(api.url) {
            headers {
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $selvbetjeningToken")
            }
            val query = """
                    {
                         notifikasjoner {
                             ...on Beskjed {
                                 lenke
                                 tekst
                                 merkelapp
                                 opprettetTidspunkt
                             }
                         }
                     }
            """.trimIndent()
            setBody(
                """{
                     "query": "$query"
                    }""".trimMarginAndNewline()
            )
        }
            .body<HttpResponse>()
    }
}

suspend fun nyBeskjed(count: Int, api: Api = Api.PRODUSENT_GCP) {
    val run = Instant.now()
    val eksterIder = generateSequence(1) { it + 1 }.iterator()
    val grupperingsid = generateSequence(1) { it + 1 }.iterator()
    val tjenester = FAGER_TESTPRODUSENT.tillatteMottakere
        .filterIsInstance<ServicecodeDefinisjon>()
        .asSequence()
        .looping()
        .iterator()
    //val virksomhetsnummere = ("0123456789".map { it.toString().repeat(9) } + listOf("910825631"))
//    .asSequence()
//    .looping()
//    .iterator()
    val virksomhetsnummere = virksomhetsnumre//listOf("910825585")//+ ("0123456789".map { it.toString().repeat(9) })
        .asSequence()
        .looping()
        .iterator()
    concurrentWithStats("nyBeskjed", count) {
        val (tjenesteKode, tjenesteVersjon) = tjenester.next()
        val virksomhet = virksomhetsnummere.next()
        client.post(api.url) {
            headers {
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
            }
            setBody(
                """{
                     "query": "mutation {
                          nyBeskjed(nyBeskjed: {
                              notifikasjon: {
                                lenke: \"https://min-side-arbeidsgiver.dev.nav.no/min-side-arbeidsgiver/?bedrift=$virksomhet\",
                                tekst: \"Dette er en test.\",
                                merkelapp: \"fager\",
                              }
                              mottaker: {
                                  altinn: {
                                      serviceCode: \"$tjenesteKode\",
                                      serviceEdition: \"$tjenesteVersjon\",
                                  }
                              },
                              metadata: {
                                eksternId: \"$run-${eksterIder.next()}\"
                                virksomhetsnummer: \"$virksomhet\"
                                grupperingsid: \"$run-${grupperingsid.next()}\"
                              }
                          }) {
                              __typename
                              ... on NyBeskjedVellykket {
                                id
                              }
                              ... on Error {
                                feilmelding
                              }
                          }
                     }"
                    }""".trimMarginAndNewline()
            )
        }
            .body<HttpResponse>().bodyAsText().also {
                require(it.contains("Vellykket")) {
                    "Feil ved opprettelse av oppgave: $it"
                }
            }
    }
}

suspend fun nyOppgave(count: Int, api: Api = Api.PRODUSENT_GCP) {
    val eksterIder = generateSequence(1) { it + 1 }.iterator()
    val grupperingsid = generateSequence(1) { it + 1 }.iterator()
    val tjenester = FAGER_TESTPRODUSENT.tillatteMottakere.take(1) // tving inntektsmelding
        .filterIsInstance<ServicecodeDefinisjon>()
        .asSequence()
        .looping()
        .iterator()
    //val virksomhetsnummere = ("0123456789".map { it.toString().repeat(9) } + listOf("910825631"))
//    .asSequence()
//    .looping()
//    .iterator()
    val virksomhetsnummere = virksomhetsnumre//listOf("910825585")//+ ("0123456789".map { it.toString().repeat(9) })
        .asSequence()
        .looping()
        .iterator()
    concurrentWithStats("nyBeskjed", count) {
        val (tjenesteKode, tjenesteVersjon) = tjenester.next()
        val virksomhet = virksomhetsnummere.next()
        client.post(api.url) {
            headers {
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
            }
            setBody(
                """{
                     "query": "mutation {
                          nyOppgave(nyOppgave: {
                              notifikasjon: {
                                lenke: \"https://min-side-arbeidsgiver.dev.nav.no/min-side-arbeidsgiver/?bedrift=$virksomhet\",
                                tekst: \"Dette er en test.\",
                                merkelapp: \"fager\",
                              }
                              mottaker: {
                                  altinn: {
                                      serviceCode: \"$tjenesteKode\",
                                      serviceEdition: \"$tjenesteVersjon\",
                                  }
                              },
                              metadata: {
                                eksternId: \"$run-${eksterIder.next()}\"
                                virksomhetsnummer: \"$virksomhet\"
                                grupperingsid: \"perftest-${grupperingsid.next()}\"
                              }
                          }) {
                              __typename
                              ... on NyOppgaveVellykket {
                                id
                              }
                              ... on Error {
                                feilmelding
                              }
                          }
                     }"
                    }""".trimMarginAndNewline()
            )
        }
            .body<HttpResponse>().bodyAsText().also {
                require(it.contains("Vellykket")) {
                    "Feil ved opprettelse av oppgave: $it"
                }
            }
    }
}

suspend fun nyOppgaveMasseproduksjon(
    virksomhet: String,
    grupperingsid: String,
    tjeneste: ServicecodeDefinisjon,
    client: HttpClient,
    api: Api
) {
    val (tjenesteKode, tjenesteVersjon) = tjeneste
    client.post(api.url) {
        headers {
            append(HttpHeaders.ContentType, "application/json")
            append(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
        }
        setBody(
            """{
                 "query": "mutation {
                      nyOppgave(nyOppgave: {
                          notifikasjon: {
                            lenke: \"https://min-side-arbeidsgiver.dev.nav.no/min-side-arbeidsgiver/?bedrift=$virksomhet\",
                            tekst: \"Dette er en test.\",
                            merkelapp: \"fager\",
                          }
                          mottaker: {
                              altinn: {
                                  serviceCode: \"$tjenesteKode\",
                                  serviceEdition: \"$tjenesteVersjon\",
                              }
                          },
                          metadata: {
                            eksternId: \"$run-${UUID.randomUUID()}\"
                            virksomhetsnummer: \"$virksomhet\"
                            grupperingsid: \"$grupperingsid\"
                          }
                      }) {
                          __typename
                          ... on NyOppgaveVellykket {
                            id
                          }
                          ... on Error {
                            feilmelding
                          }
                      }
                 }"
            }""".trimMarginAndNewline()
        )
    }
        .body<HttpResponse>().bodyAsText().also {
            require(it.contains("Vellykket")) {
                "Feil ved opprettelse av oppgave: $it"
            }
        }


}

suspend fun nySak(count: Int, api: Api = Api.PRODUSENT_GCP) {
    val navn = listOf("Tor", "Per", "Ragnhild", "Muhammed", "Sara", "Alex", "Nina")
    val typeSak = listOf("er syk", "har lønnstilskudd", "har mentortilskudd")
    fun genererTittel() = "${navn.random()} ${typeSak.random()}."
    val grupperingsid = generateSequence(1) { it + 1 }.iterator()
    val tjenester = FAGER_TESTPRODUSENT.tillatteMottakere.take(1) // tving inntektsmelding
        .filterIsInstance<ServicecodeDefinisjon>()
        .asSequence()
        .looping()
        .iterator()
//val virksomhetsnummere = ("0123456789".map { it.toString().repeat(9) } + listOf("910825631"))
//    .asSequence()
//    .looping()
//    .iterator()
    val virksomhetsnummere = virksomhetsnumre//listOf("910825585")//+ ("0123456789".map { it.toString().repeat(9) })
        .asSequence()
        .looping()
        .iterator()
    concurrentWithStats("nySak", count) {
        val (tjenesteKode, tjenesteVersjon) = tjenester.next()
        val virksomhet = virksomhetsnummere.next()
        client.post(api.url) {
            headers {
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
            }
            setBody(
                """{
                     "query": "mutation {
                          nySak(
                              grupperingsid: \"perftest-${grupperingsid.next()}\"
                              merkelapp: \"fager\"
                              virksomhetsnummer: \"$virksomhet\"
                              mottakere: [
                                  {altinn: {
                                      serviceCode: \"$tjenesteKode\",
                                      serviceEdition: \"$tjenesteVersjon\"
                                  }}
                              ]
                              
                              tittel: \"${genererTittel()}\",                              
                              lenke: \"https://min-side-arbeidsgiver.dev.nav.no/min-side-arbeidsgiver/?bedrift=$virksomhet\",
                              initiellStatus: MOTTATT
                          ) {
                              __typename
                              ... on NySakVellykket {
                                id
                              }
                              ... on Error {
                                feilmelding
                              }
                          }
                     }"
                    }""".trimMarginAndNewline()
            )
        }
            .body<HttpResponse>().bodyAsText().also {
                require(it.contains("Vellykket")) {
                    "Feil ved opprettelse av sak: $it"
                }
            }
    }

}

fun wannabeBetaIshDistribution(x: Int, antallVirksomheter: Int, maxSaker: Int): Int {
    val k = kotlin.math.ln(maxSaker.toDouble()) / antallVirksomheter
    return kotlin.math.exp(k * x).toInt()
}


suspend fun nyeSakerMedOppgaver(
    maxSaker: Int,
    api: Api = Api.PRODUSENT_GCP,
    client: HttpClient,
    antallVirksomheter: Int
) {
    val virksomheterAntallSaker: Map<String, Int> = generateSequence {
        Random.nextLong(100000000, 999999999).toString()
    }
        .take(antallVirksomheter)
        .mapIndexed { index, virksomhetsnr ->
            virksomhetsnr to wannabeBetaIshDistribution(
                index,
                virksomhetsnumre.size,
                maxSaker
            )
        }
        .toMap()
    val tjenester = FAGER_TESTPRODUSENT.tillatteMottakere.drop(1)
        .filterIsInstance<ServicecodeDefinisjon>()
        .asSequence()
        .looping()
        .iterator()
    val grupperingsidSequence = generateSequence(1) { it + 1 }.iterator()

    virksomheterAntallSaker.forEach { (virksomhetsnummer, antallSaker) ->
        val tjeneste = tjenester.next()
        (1..antallSaker).map {
            var grupperingsid = "${Instant.now()}${grupperingsidSequence.next()}"
            nySakMasseproduksjon(virksomhetsnummer, grupperingsid, tjeneste, client, api)
            (1..(1..4).random()).map {
                nyOppgaveMasseproduksjon(virksomhetsnummer, grupperingsid, tjeneste, client, api)
            }
        }
    }


}

suspend fun nySakMasseproduksjon(
    virksomhet: String,
    grupperingsid: String,
    tjeneste: ServicecodeDefinisjon,
    client: HttpClient,
    api: Api
) {
    val navn = listOf("Tor", "Per", "Ragnhild", "Muhammed", "Sara", "Alex", "Nina")
    val typeSak = listOf("er syk", "har lønnstilskudd", "har mentortilskudd")
    val merkelapp = listOf("fager", "fager2", "fager3")

    fun genererTittel() = "${navn.random()} ${typeSak.random()}."

    val (tjenesteKode, tjenesteVersjon) = tjeneste
    client.post(api.url) {
        headers {
            append(HttpHeaders.ContentType, "application/json")
            append(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
        }
        setBody(
            """{
                 "query": "mutation {
                      nySak(
                          grupperingsid: \"$grupperingsid\"
                          merkelapp: \"${merkelapp.random()}\"
                          virksomhetsnummer: \"$virksomhet\"
                          mottakere: [
                              {altinn: {
                                  serviceCode: \"$tjenesteKode\",
                                  serviceEdition: \"$tjenesteVersjon\"
                              }}
                          ]
                          
                          tittel: \"${genererTittel()}\",                              
                          lenke: \"https://min-side-arbeidsgiver.dev.nav.no/min-side-arbeidsgiver/?bedrift=$virksomhet\",
                          initiellStatus: MOTTATT
                      ) {
                          __typename
                          ... on NySakVellykket {
                            id
                          }
                          ... on Error {
                            feilmelding
                          }
                      }
                 }"
            }""".trimMarginAndNewline()
        )
    }
        .body<HttpResponse>().bodyAsText().also {
            require(it.contains("Vellykket")) {
                "Feil ved opprettelse av sak: $it"
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


val run = Instant.now()!!
val virksomhetsnumre = listOf(
    "315066786",
    "315064295",
    "999999999",
    "315064562",
    "315064228",
    "315063892",
    "315066360",
    "315064392",
    "315065933",
    "215065162",
    "315065534",
    "315063876",
    "215067742",
    "315064937",
    "315067073",
    "315067456",
    "315065682",
    "315064945",
    "315066166",
    "315065925",
    "315067081",
    "315064112",
    "315067227",
    "315064449",
    "315066905",
    "315064201",
    "315063906",
    "315065720",
    "315065860",
    "315064511",
    "315064902",
    "315064872",
    "315064260",
    "315065771",
    "315065410",
    "315064104",
    "315067022",
    "315066808",
    "315064899",
    "315067774",
    "215067262",
    "315067642",
    "315065488",
    "315064430",
    "215064522",
    "215066932",
    "315065151",
    "315063981",
    "315064538",
    "315065011",
    "315066379",
    "315065976",
    "315065097",
    "215067432",
    "315066034",
    "315067359",
    "315067537",
    "315064465",
    "315064090",
    "315066484",
    "315065208",
    "315065356",
    "315067014",
    "315065402",
    "315064805",
    "315067545",
    "315066859",
    "315066468",
    "315065100",
    "315065828",
    "315066832",
    "315067669",
    "315064015",
    "315065046",
    "315065429",
    "315066948",
    "315065585",
    "315067197",
    "315064546",
    "215065022",
    "315065666",
    "315066689",
    "315065119",
    "315066883",
    "315064570",
    "315065062",
    "315067405",
    "215064832",
    "315066220",
    "315065461",
    "315067413",
    "315064848",
    "315066522",
    "315064813",
    "315065798",
    "315064422",
    "315064473",
    "315067383",
    "315067650",
    "315067219",
    "315064376",
    "315065615",
    "315064171",
    "315065259",
    "315066395",
    "315064031",
    "315064651",
    "315064244",
    "315065291",
    "315064600",
    "315066972",
    "315065135",
    "315064694",
    "315579740",
    "313591441",
    "315065143",
    "215065952",
    "315064082",
    "315066247",
    "315064988",
    "315066794",
    "215064492",
    "315064791",
    "315065305",
    "315066387",
    "315067626",
    "315066328",
    "315064481",
    "315065739",
    "315064333",
    "315066735",
    "315064821",
    "315067324",
    "315064767",
    "315064740",
    "315067030",
    "315066123",
    "315066190",
    "315067472",
    "315066646",
    "315065518",
    "315064856",
    "315066182",
    "315064775",
    "315065380",
    "315067065",
    "315066085",
    "315066255",
    "315067278",
    "315065526",
    "315067332",
    "315065631",
    "315067634",
    "315064384",
    "315064996",
    "315067715",
    "315066271",
    "315067154",
    "215066452",
    "315065887",
    "315067375",
    "215065502",
    "315065186",
    "315063930",
    "215064182",
    "315066174",
    "315064686",
    "315066751",
    "315064341",
    "315063957",
    "315066727",
    "215064972",
    "215064352",
    "315065399",
    "315066298",
    "315066999",
    "315066158",
    "315066131",
    "315065755",
    "315065852",
    "315066867",
    "315066581",
    "315065283",
    "315066549",
    "315066425",
    "315064236",
    "315065070",
    "215066762",
    "315065712",
    "315067316",
    "315066077",
    "315066441",
    "315066778",
    "215067602",
    "215063852",
    "315066824",
    "315066565",
    "315067421",
    "315064783",
    "315063973",
    "315064163",
    "315063914",
    "315064287",
    "315067189",
    "315065453",
    "315065240",
    "315064120",
    "215065812",
    "315067006",
    "315066638",
    "315067146",
    "315065054",
    "315064724",
    "315067057",
    "315066603",
    "315064414",
    "315066093",
    "315063922",
    "315066611",
    "315067464",
    "215066282",
    "315067782",
    "315067758",
    "315064619",
    "315067804",
    "315065836",
    "315063833",
    "315067588",
    "315065321",
    "215066592",
    "315066700",
    "215067092",
    "315064864",
    "315065038",
    "215066312",
    "315066662",
    "315066212",
    "315064139",
    "315064279",
    "315064252",
    "315065496",
    "315065577",
    "315064961",
    "315064910",
    "315065917",
    "315067448",
    "315066409",
    "315063868",
    "315067111",
    "315064457",
    "315065879",
    "315065437",
    "315066697",
    "315067170",
    "315066506",
    "315064635",
    "315066921",
    "315066956",
    "315064759",
    "315066514",
    "215064212",
    "315067499",
    "315067685",
    "315066654",
    "315066573",
    "315066670",
    "315066263",
    "315066239",
    "315065763",
    "315066069",
    "315066417",
    "315067251",
    "315067820",
    "315066336",
    "315067510",
    "315065593",
    "315065224",
    "315066492",
    "315067138",
    "315067200",
    "315067286",
    "315064589",
    "315064074",
    "315067049",
    "315066107",
    "315067812",
    "315063884",
    "315067731",
    "315065348",
    "315064325",
    "315066018",
    "315066433",
    "315067596",
    "315065089",
    "315065801",
    "215065782",
    "315067480",
    "315065704",
    "315066204",
    "315065747",
    "315063949",
    "315067553",
    "315064678",
    "315065623",
    "315066840",
    "315066913",
    "315065690",
    "215065332",
    "315064406",
    "315065364",
    "315065895",
    "315067308",
    "315064066",
    "315065216",
    "315066719",
    "215066142",
    "315067103",
    "315065909",
    "315064503",
    "315064708",
    "315066050",
    "315066042",
    "315065127",
    "315065445",
    "315064716",
    "315067529",
    "315064597",
    "215066622",
    "315064929",
    "315065267",
    "315065674",
    "315065232",
    "315066344",
    "315066816",
    "315067235",
    "315065275",
    "315065941",
    "315064309",
    "315067294",
    "315067677",
    "315067367",
    "315067723",
    "315066115",
    "215064662",
    "315066476",
    "215066002",
    "315067340",
    "315064554",
    "315066875",
    "315065569",
    "315065968",
    "315066352",
    "315065194",
    "215067122",
    "315067766",
    "315067618",
    "315065984",
    "315064198",
    "315066980",
    "315064627",
    "315065607",
    "315064023",
    "315066964",
    "315065372",
    "315067162",
    "215063992",
    "315064732",
    "315064007",
    "315065992",
    "315064880",
    "315065844",
    "310204463",
    "215064042",
    "315065550",
    "315066891",
    "315067561",
    "315066530",
    "215065472",
    "315067391",
    "315063965",
    "315065178",
    "315065658",
    "315064368",
    "315065313",
    "315067707",
    "315064643",
    "315065003",
    "315064155",
    "315066557",
    "315064147",
    "215065642",
    "315064317",
    "315067502",
    "315064953",
    "215067572",
    "315065542",
    "313776301",
    "315066301",
    "315064058",
    "315066743",
    "315067693",
    "315063841",
    "315067243",
    "315066026",
    "315067790",
)
