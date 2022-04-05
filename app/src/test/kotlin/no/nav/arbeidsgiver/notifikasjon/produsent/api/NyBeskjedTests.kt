package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.DoNotParallelize
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

@Suppress("NAME_SHADOWING")
@ExperimentalTime
@DoNotParallelize
class NyBeskjedTests : DescribeSpec({
    val embeddedKafka = embeddedKafka()

    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val engine = ktorProdusentTestServer(
        kafkaProducer = embeddedKafka.newProducer(),
        produsentRepository = produsentRepository,
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
                                virksomhetsnummer: "42"
                            }
                        }) {
                            __typename
                            ... on NyBeskjedVellykket {
                                id
                                eksterneVarsler {
                                    id
                                }
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
            val nyBeskjed = response.getTypedContent<MutationNyBeskjed.NyBeskjedResultat>("nyBeskjed")
            nyBeskjed should beOfType<MutationNyBeskjed.NyBeskjedVellykket>()
        }

        it("sends message to kafka") {
            val consumer = embeddedKafka.newConsumer()
            val poll = consumer.poll(seconds(5).toJavaDuration())
            val value = poll.last().value()
            value should beOfType<BeskjedOpprettet>()
            val event = value as BeskjedOpprettet
            val nyBeskjed = response.getTypedContent<MutationNyBeskjed.NyBeskjedVellykket>("nyBeskjed")
            event.notifikasjonId shouldBe nyBeskjed.id
            event.lenke shouldBe "https://foo.bar"
            event.tekst shouldBe "hello world"
            event.merkelapp shouldBe "tag"
            event.mottakere.single() shouldBe NærmesteLederMottaker(
                naermesteLederFnr = "12345678910",
                ansattFnr = "321",
                virksomhetsnummer = "42"
            )
            event.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
        }

        it("updates produsent modell") {
            val id = response.getTypedContent<UUID>("nyBeskjed/id")
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }
    }
})

