package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class NyOppgaveTests : DescribeSpec({
    val embeddedKafka = embeddedKafka()
    val produsentModel = mockk<ProdusentModel>(relaxed = true)
    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = embeddedKafka.newProducer(),
            produsentRegister = mockProdusentRegister,
            produsentModel = produsentModel,
        )
    )

    describe("produsent-api happy path") {
        val response = engine.produsentApi(
            """
                    mutation {
                        nyOppgave(nyOppgave: {
                            mottaker: {
                                naermesteLeder: {
                                    naermesteLederFnr: "12345678910",
                                    ansattFnr: "321"
                                    virksomhetsnummer: "42"
                                } 
                            }
                            notifikasjon: {
                                lenke: "https://foo.bar",
                                tekst: "hello world",
                                merkelapp: "tag",
                            }
                            metadata: {
                                eksternId: "heu",
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }
                        }) {
                            __typename
                            ... on NyOppgaveVellykket {
                                id
                            }
                        }
                    }
                """.trimIndent()
        )

        it("status is 200 OK") {
            response.status() shouldBe HttpStatusCode.OK
        }

        it("response inneholder ikke feil") {
            response.getGraphqlErrors() should beEmpty()
        }

        it("respons inneholder forventet data") {
            val nyOppgave = response.getTypedContent<ProdusentAPI.NyOppgaveResultat>("nyOppgave")
            nyOppgave should beOfType<ProdusentAPI.NyOppgaveVellykket>()
        }

        it("sends message to kafka") {
            val consumer = embeddedKafka.newConsumer()
            val poll = consumer.poll(seconds(5).toJavaDuration())
            val value = poll.last().value()
            value should beOfType<Hendelse.OppgaveOpprettet>()
            val event = value as Hendelse.OppgaveOpprettet
            val nyOppgave = response.getTypedContent<ProdusentAPI.NyOppgaveVellykket>("nyOppgave")
            event.notifikasjonId shouldBe nyOppgave.id
            event.lenke shouldBe "https://foo.bar"
            event.tekst shouldBe "hello world"
            event.merkelapp shouldBe "tag"
            event.mottaker shouldBe NærmesteLederMottaker(
                naermesteLederFnr = "12345678910",
                ansattFnr = "321",
                virksomhetsnummer = "42"
            )
            event.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
        }

        it("updates produsent modell") {
            coVerify {
                produsentModel.oppdaterModellEtterHendelse(any())
            }
        }
    }
})

