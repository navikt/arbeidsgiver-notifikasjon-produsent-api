@file:Suppress("NAME_SHADOWING")

package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*


class SoftDeleteNotifikasjonTests : DescribeSpec({

    val virksomhetsnummer = "123"
    val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    val uuid2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
    val merkelapp = "tag"
    val eksternId = "123"
    val eksternId2 = "234"
    val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

    val oppgaveOpprettet = OppgaveOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        eksternId = eksternId,
        mottakere = listOf(mottaker),
        hendelseId = uuid,
        notifikasjonId = uuid,
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = opprettetTidspunkt,
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = null,
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )
    val oppgaveOpprettet2 = oppgaveOpprettet.copy(
        eksternId = eksternId2,
        hendelseId = uuid2,
        notifikasjonId = uuid2,
    )

    describe("SoftDelete-oppførsel") {


        context("Eksisterende oppgave blir markert som slettet") {
            val (produsentModel, kafkaProducer, engine) = setupEngine()


            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on SoftDeleteNotifikasjonVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer tilbake id-en") {
                val vellykket =
                    response.getTypedContent<MutationSoftDeleteNotifikasjon.SoftDeleteNotifikasjonVellykket>("softDeleteNotifikasjon")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                kafkaProducer.hendelser.removeLast().also {
                    it shouldBe instanceOf<SoftDelete>()
                }
            }

            it("har slettet-status i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)!!
                notifikasjon.deletedAt shouldNotBe null
            }
            it("notifikasjon2 har ikke slettet-status i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid2)!!
                notifikasjon.deletedAt shouldBe null
            }


            it("mineNotifikasjoner rapporterer som softDeleted") {
                val response = engine.produsentApi(
                    """
                        query {
                            mineNotifikasjoner(merkelapp: "$merkelapp") {
                            __typename
                            ... on NotifikasjonConnection {
                                __typename
                                pageInfo {
                                    hasNextPage
                                    endCursor
                                }
                                edges {
                                    cursor
                                    node {
                                      __typename
                                      ... on Beskjed {
                                        mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        mottakere {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid
                                            softDeleted
                                            softDeletedAt
                                        }
                                        beskjed {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                      }
                                      ... on Oppgave {
                                      mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        mottakere {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid
                                            softDeleted
                                            softDeletedAt
                                        }
                                        oppgave {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                            tilstand
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                      }
                                    }
                                }
                            }
                        }
                    }
                    """
                )
                val slettetNotifikasjon =
                    response.getTypedContent<QueryNotifikasjoner.NotifikasjonConnection>("mineNotifikasjoner")
                        .edges
                        .map { it.node }
                        .find { it.id == uuid }!!

                slettetNotifikasjon.metadata.softDeleted shouldBe true
                slettetNotifikasjon.metadata.softDeletedAt shouldNotBe null
            }
        }

        context("Oppgave mangler") {
            val (_, _, engine) = setupEngine()
            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjon")
            }
        }

        context("Oppgave med feil merkelapp") {
            val (produsentModel, _, engine) = setupEngine()
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.UgyldigMerkelapp>("softDeleteNotifikasjon")
            }
        }
    }

    describe("softDeleteNotifikasjonByEksternId-oppførsel") {

        context("Eksisterende oppgave blir markert som slettet") {
            val (produsentModel, kafkaProducer, engine) = setupEngine()

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on SoftDeleteNotifikasjonVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer tilbake id-en") {
                val vellykket =
                    response.getTypedContent<MutationSoftDeleteNotifikasjon.SoftDeleteNotifikasjonVellykket>("softDeleteNotifikasjonByEksternId")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                kafkaProducer.hendelser.removeLast().also {
                    it shouldBe instanceOf<SoftDelete>()
                }
            }

            it("har fått slettet tidspunkt") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)!!
                notifikasjon.deletedAt shouldNotBe null
            }
            it("oppgave 2 har ikke fått slettet tidspunkt") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid2)!!
                notifikasjon.deletedAt shouldBe null
            }
        }

        context("Oppgave mangler") {
            val (_, _, engine) = setupEngine()
            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil merkelapp men riktig eksternId") {
            val (produsentModel, _, engine) = setupEngine()

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil eksternId men riktig merkelapp") {
            val (produsentModel, _, engine) = setupEngine()
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }
    }
})

private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val kafkaProducer = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentModel
    )
    return Triple(produsentModel, kafkaProducer, engine)
}
