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
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
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
class NyBeskjedTests : DescribeSpec({
    val embeddedKafka = embeddedKafka()
    val real = ProdusentModel.Oppgave::class
    val produsentRepository = mockk<ProdusentRepository>(relaxed = true)

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = embeddedKafka.newProducer(),
            produsentRegister = stubProdusentRegister,
            produsentRepository = produsentRepository,
        )
    )

    describe("produsent-api happy path") {
        val response = engine.produsentApi(
            """
                    mutation {
                        nyBeskjed(nyBeskjed: {
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
                            ... on NyBeskjedVellykket {
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
            val nyBeskjed = response.getTypedContent<ProdusentAPI.NyBeskjedResultat>("nyBeskjed")
            nyBeskjed should beOfType<ProdusentAPI.NyBeskjedVellykket>()
        }

        it("sends message to kafka") {
            val consumer = embeddedKafka.newConsumer()
            val poll = consumer.poll(seconds(5).toJavaDuration())
            val value = poll.last().value()
            value should beOfType<Hendelse.BeskjedOpprettet>()
            val event = value as Hendelse.BeskjedOpprettet
            val nyBeskjed = response.getTypedContent<ProdusentAPI.NyBeskjedVellykket>("nyBeskjed")
            event.notifikasjonId shouldBe nyBeskjed.id
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
                produsentRepository.oppdaterModellEtterHendelse(any())
            }
        }
    }
})

