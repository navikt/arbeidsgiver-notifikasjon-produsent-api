package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*

// Legg til en test for sletting av én av flere rader i database

class HardDeleteSakTests : DescribeSpec({

    val virksomhetsnummer = "123"
    val sakId = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    val sakId2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
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
        hendelseId = sakId,
        sakId = sakId,
        tittel = "test",
        lenke = "https://nav.no",
        oppgittTidspunkt = opprettetTidspunkt,
        mottattTidspunkt = opprettetTidspunkt,
        kildeAppNavn = "",
        produsentId = "",
        hardDelete = null,
    )
    val beskjedOpprettet = BeskjedOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        grupperingsid = grupperingsid,
        mottakere = listOf(mottaker),
        hendelseId = uuid("11"),
        notifikasjonId =  uuid("11"),
        sakId = sakId,
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = opprettetTidspunkt,
        kildeAppNavn = "",
        produsentId = "",
        hardDelete = null,
        eksternId = "11",
        eksterneVarsler = emptyList(),
    )
    val sakOpprettet2 = sakOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = sakId2,
        sakId = sakId2,
    )
    val beskedOpprettet2 = beskjedOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = uuid("12"),
        notifikasjonId =  uuid("12"),
        sakId = sakId2,
        eksternId = "12",
    )

    describe("HardDelete-oppførsel Sak") {
        context("Eksisterende sak blir slettet") {
            val (produsentModel, kafkaProducer, engine) = setupEngine()

            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)
            produsentModel.oppdaterModellEtterHendelse(beskedOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$sakId") {
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
                vellykket.id shouldBe sakId
            }

            it("har sendt melding til kafka") {
                kafkaProducer.hendelser.removeLast().also {
                    it shouldBe instanceOf<HardDelete>()
                }
            }

            it("sak1 har blitt fjernet fra modellen") {
                val sak = produsentModel.hentSak(sakId)
                sak shouldBe null
            }
            it("beskjed1 har blitt fjernet fra modellen") {
                val beskjed1 = produsentModel.hentNotifikasjon(beskjedOpprettet.notifikasjonId)
                beskjed1 shouldBe null
            }
            it("sak2 har ikke blitt fjernet fra modellen") {
                val sak = produsentModel.hentSak(sakId2)
                sak shouldNotBe null
            }
            it("beskjed2 har ikke blitt fjernet fra modellen") {
                val beskjed2 = produsentModel.hentNotifikasjon(beskedOpprettet2.notifikasjonId)
                beskjed2 shouldNotBe null
            }
        }

        context("Sak mangler") {
            val (_, _, engine) = setupEngine()
            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$sakId") {
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
            val (produsentModel, _, engine) = setupEngine()
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$sakId") {
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
            val (produsentModel, kafkaProducer, engine) = setupEngine()
            kafkaProducer.clear()
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
                vellykket.id shouldBe sakId
            }

            it("har sendt melding til kafka") {
                kafkaProducer.hendelser.removeLast().also {
                    it shouldBe instanceOf<HardDelete>()
                }
            }

            it("finnes ikke i modellen") {
                val sak = produsentModel.hentSak(sakId)
                sak shouldBe null
            }

            it("sak2 finnes fortsatt i modellen") {
                val sak = produsentModel.hentSak(sakId2)
                sak shouldNotBe null
            }

            it("opprettelse av ny sak med samme merkelapp og grupperingsid feiler") {
                engine.produsentApi(
                    """
                    mutation {
                        nySak(
                            virksomhetsnummer: "1"
                            merkelapp: "$merkelapp"
                            grupperingsid: "$grupperingsid"
                            mottakere: [{
                                altinn: {
                                    serviceCode: "5441"
                                    serviceEdition: "1"
                                }
                            }]
                            initiellStatus: MOTTATT
                            tidspunkt: "2020-01-01T01:01Z"
                            tittel: "ny sak"
                            lenke: "#foo"
                        ) {
                            __typename
                            ... on NySakVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
                ).getTypedContent<Error.DuplikatGrupperingsidEtterDelete>("nySak")
            }
        }

        context("Sak mangler") {
            val (_, _, engine) = setupEngine()
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
            val (produsentModel, _, engine) = setupEngine()
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
            val (produsentModel, _, engine) = setupEngine()
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