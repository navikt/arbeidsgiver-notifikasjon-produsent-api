@file:Suppress("NAME_SHADOWING")

package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakOpprettet
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

class HardDeleteSakTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val kafkaProducer = mockk<CoroutineKafkaProducer<KafkaKey, Hendelse>>()

    mockkStatic(CoroutineKafkaProducer<KafkaKey, Hendelse>::sendHendelse)
    coEvery { any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<HardDelete>()) } returns Unit

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

    describe("HardDelete-oppførsel Sak") {
        context("Eksisterende sak blir slettet") {

            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$uuid") {
                        __typename
                        ... on HardDeleteSakVellykket {
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
                    response.getTypedContent<MutationHardDeleteSak.HardDeleteSakVellykket>("hardDeleteSak")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<HardDelete>())
                }
            }

            it("har blitt fjernet fra modellen") {
                val sak = produsentModel.hentSak(uuid)
                sak shouldBe null
            }
            it("sak2 har ikke blitt fjernet fra modellen") {
                val sak = produsentModel.hentSak(uuid2)
                sak shouldNotBe null
            }
        }

        context("Sak mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSak")
            }
        }

        context("Sak med feil merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.UgyldigMerkelapp>("hardDeleteSak")
            }
        }
    }

    describe("hardDeleteSakByEksternId-oppførsel") {
        context("Eksisterende sak blir slettet") {

            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on HardDeleteSakVellykket {
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
                val vellykket = response.getTypedContent<MutationHardDeleteSak.HardDeleteSakVellykket>("hardDeleteSakByGrupperingsid")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(ofType<HardDelete>())
                }
            }

            it("finnes ikke i modellen") {
                val sak = produsentModel.hentSak(uuid)
                sak shouldBe null
            }

            it("sak2 finnes fortsatt i modellen") {
                val sak = produsentModel.hentSak(uuid2)
                sak shouldNotBe null
            }
        }

        context("Sak mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSakByGrupperingsid")
            }
        }

        context("Sak med feil merkelapp men riktig eksternId") {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSakByGrupperingsid")
            }
        }

        context("Sak med feil grupperingsid men riktig merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "nope$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSakByGrupperingsid")
            }
        }
    }
})