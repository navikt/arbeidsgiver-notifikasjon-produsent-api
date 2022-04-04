@file:Suppress("NAME_SHADOWING")

package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
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


class SoftDeleteSakTests : DescribeSpec({

    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val kafkaProducer = mockk<CoroutineKafkaProducer<KafkaKey, Hendelse>>()

    mockkStatic(CoroutineKafkaProducer<KafkaKey, Hendelse>::sendHendelse)
    coEvery { any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<SoftDelete>()) } returns Unit

    afterSpec {
        unmockkAll()
    }

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        )
    )

    val virksomhetsnummer = "123"
    val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    val uuid2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
    val merkelapp = "tag"
    val grupperingsid = "123"
    val grupperingsid2 = "234"
    val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

    val sakOpprettet = SakOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        grupperingsid = grupperingsid,
        mottakere = listOf(mottaker),
        hendelseId = uuid,
        sakId = uuid,
        tittel = "test",
        lenke = "https://nav.no",
        oppgittTidspunkt = opprettetTidspunkt,
        mottattTidspunkt = opprettetTidspunkt,
        kildeAppNavn = "",
        produsentId = "",
    )
    val sakOpprettet2 = sakOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = uuid2,
        sakId = uuid2,
    )

    describe("Sak SoftDelete-oppførsel") {
        context("Eksisterende sak blir markert som slettet") {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteSak(id: "$uuid") {
                        __typename
                        ... on SoftDeleteSakVellykket {
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
                    response.getTypedContent<MutationSoftDeleteSak.SoftDeleteSakVellykket>("softDeleteSak")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<SoftDelete>())
                }
            }

            it("har slettet-status i modellen") {
                val notifikasjon = produsentModel.hentSak(uuid)!!
                notifikasjon.deletedAt shouldNotBe null
            }
            it("notifikasjon2 har ikke slettet-status i modellen") {
                val notifikasjon = produsentModel.hentSak(uuid2)!!
                notifikasjon.deletedAt shouldBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteSak(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("softDeleteSak")
            }
        }

        context("Oppgave med feil merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteSak(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.UgyldigMerkelapp>("softDeleteSak")
            }
        }
    }

    describe("softDeleteSakByGrupperingsid-oppførsel") {

        context("Eksisterende oppgave blir markert som slettet") {

            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on SoftDeleteSakVellykket {
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
                    response.getTypedContent<MutationSoftDeleteSak.SoftDeleteSakVellykket>("softDeleteSakByGrupperingsid")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<SoftDelete>())
                }
            }

            it("har fått slettet tidspunkt") {
                val notifikasjon = produsentModel.hentSak(uuid)!!
                notifikasjon.deletedAt shouldNotBe null
            }
            it("oppgave 2 har ikke fått slettet tidspunkt") {
                val notifikasjon = produsentModel.hentSak(uuid2)!!
                notifikasjon.deletedAt shouldBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("softDeleteSakByGrupperingsid")
            }
        }

        context("Oppgave med feil merkelapp men riktig grupperingsid") {

            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("softDeleteSakByGrupperingsid")
            }
        }

        context("Oppgave med feil grupperingsid men riktig merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "nope$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("softDeleteSakByGrupperingsid")
            }
        }
    }
})
