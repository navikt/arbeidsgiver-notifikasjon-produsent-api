package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.instanceOf
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
class NyOppgaveTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val kafkaProducer = mockk<HendelseProdusent>()
    coEvery { kafkaProducer.send(any()) } returns Unit

    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentRepository,
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
                                hardDelete: {
                                  den: "2019-10-13T07:20:50.52"
                                }
                            }
                        }) {
                            __typename
                            ... on NyOppgaveVellykket {
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
            val nyOppgave = response.getTypedContent<MutationNyOppgave.NyOppgaveResultat>("nyOppgave")
            nyOppgave should beOfType<MutationNyOppgave.NyOppgaveVellykket>()
        }

        val nyOppgave = response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")

        it("sends message to kafka") {
            coVerify {
                kafkaProducer.send(withArg { oppgaveOpprettet: OppgaveOpprettet ->
                    oppgaveOpprettet.notifikasjonId shouldBe nyOppgave.id
                    oppgaveOpprettet.lenke shouldBe "https://foo.bar"
                    oppgaveOpprettet.tekst shouldBe "hello world"
                    oppgaveOpprettet.merkelapp shouldBe "tag"
                    oppgaveOpprettet.mottakere.single() shouldBe NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                    oppgaveOpprettet.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                    oppgaveOpprettet.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
                })
            }
        }

        it("updates produsent modell") {
            val id = response.getTypedContent<UUID>("nyOppgave/id")
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }
    }
})

