@file:Suppress("NAME_SHADOWING")

package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.DoNotParallelize
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.produsent.*
import no.nav.arbeidsgiver.notifikasjon.produsent.api.*
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

// Legg til en test for sletting av én av flere rader i database

@DoNotParallelize
class HardDeleteNotifikasjonTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val kafkaProducer = mockk<CoroutineKafkaProducer<KafkaKey, Hendelse>>()

    mockkStatic(CoroutineKafkaProducer<KafkaKey, Hendelse>::sendHendelse)
    coEvery { any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<HardDelete>()) } returns Unit

    afterSpec {
        unmockkAll()
    }

    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentModel
    )

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
    )
    val oppgaveOpprettet2 = oppgaveOpprettet.copy(
        eksternId = eksternId2,
        hendelseId = uuid2,
        notifikasjonId = uuid2,
    )

    describe("HardDelete-oppførsel") {

        context("Eksisterende oppgave blir slettet") {

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on HardDeleteNotifikasjonVellykket {
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
                    response.getTypedContent<MutationHardDeleteNotifikasjon.HardDeleteNotifikasjonVellykket>("hardDeleteNotifikasjon")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<HardDelete>())
                }
            }

            it("har blitt fjernet fra modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)
                notifikasjon shouldBe null
            }
            it("notifikasjon2 har ikke blitt fjernet fra modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid2)
                notifikasjon shouldNotBe null
            }

            it("mineNotifikasjoner rapporterer at notifikasjon ikke finnes") {
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
                    response
                        .getTypedContent<JsonNode>("mineNotifikasjoner")["edges"]
                        .toList()
                        .map { it["node"] }
                        .find { it["id"]?.asText() == uuid.toString() }

                slettetNotifikasjon shouldBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjon")
            }
        }

        context("Oppgave med feil merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.UgyldigMerkelapp>("hardDeleteNotifikasjon")
            }
        }
    }

    describe("hardDeleteNotifikasjonByEksternId-oppførsel") {


        context("Eksisterende oppgave blir slettet") {

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on HardDeleteNotifikasjonVellykket {
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
                    response.getTypedContent<MutationHardDeleteNotifikasjon.HardDeleteNotifikasjonVellykket>("hardDeleteNotifikasjonByEksternId")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<HardDelete>())
                }
            }

            it("finnes ikke i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)
                notifikasjon shouldBe null
            }

            it("oppgave2 finnes fortsatt i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid2)
                notifikasjon shouldNotBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil merkelapp men riktig eksternId") {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil eksternId men riktig merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
            }
        }
    }
})