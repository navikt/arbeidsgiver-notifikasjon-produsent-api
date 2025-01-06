package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*


class SoftDeleteSakTests : DescribeSpec({

    val virksomhetsnummer = "123"
    val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    val uuid2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
    val uuid3 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cb")
    val uuid4 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cc")
    val uuid5 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ce")
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
        nesteSteg = null,
        hardDelete = null,
        tilleggsinformasjon = null
    )
    val sakOpprettet2 = sakOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = uuid2,
        sakId = uuid2,
    )

    val oppgaveOpprettet = OppgaveOpprettet(
        eksternId = "1",
        grupperingsid = grupperingsid,
        merkelapp = merkelapp,
        frist = null,
        tekst = "test",
        sakId = uuid,
        opprettetTidspunkt = opprettetTidspunkt,
        eksterneVarsler = listOf(),
        mottakere = listOf(mottaker),
        hardDelete = null,
        hendelseId = UUID.randomUUID(),
        kildeAppNavn = "",
        lenke = "https://nav.no",
        produsentId = "",
        notifikasjonId = uuid3,
        virksomhetsnummer = "1",
        påminnelse = null,
    )

    val oppgaveOpprettet2 = oppgaveOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = uuid4,
        notifikasjonId = uuid4,
        eksternId = "2",
    )

    val beskjedOpprettet = HendelseModel.BeskjedOpprettet(
        eksternId = "3",
        grupperingsid = grupperingsid,
        merkelapp = merkelapp,
        tekst = "test",
        opprettetTidspunkt = opprettetTidspunkt,
        eksterneVarsler = listOf(),
        mottakere = listOf(mottaker),
        hardDelete = null,
        hendelseId = UUID.randomUUID(),
        kildeAppNavn = "",
        lenke = "https://nav.no",
        produsentId = "",
        notifikasjonId = uuid5,
        virksomhetsnummer = "1",
        sakId = uuid,
    )

    describe("Sak SoftDelete-oppførsel") {
        context("Eksisterende sak blir markert som slettet") {
            val (produsentModel, kafkaProducer, engine) = setupEngine()
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
                kafkaProducer.hendelser.removeLast().also {
                    it shouldBe instanceOf<SoftDelete>()
                }
            }

            it("har blitt slettet i modellen") {
                val notifikasjon = produsentModel.hentSak(uuid)
                notifikasjon shouldBe null
            }
            it("notifikasjon2 har ikke slettet-status i modellen") {
                val notifikasjon = produsentModel.hentSak(uuid2)!!
                notifikasjon.deletedAt shouldBe null
            }
        }

        context("Oppgave mangler") {
            val (_, _, engine) = setupEngine()
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
            val (produsentModel, _, engine) = setupEngine()
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

        context("SoftDelete cascader fra sak til notifikasjoner"){
            val (produsentModel, _, engine) = setupEngine()
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)
            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)


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

            it ("sak er slettet") {
                val vellykket =
                    response.getTypedContent<MutationSoftDeleteSak.SoftDeleteSakVellykket>("softDeleteSak")
                vellykket.id shouldBe uuid
            }

            it ("oppgave er slettet") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid3)
                notifikasjon shouldBe null
            }

            it ("oppgave 2 er ikke slettet") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid4)!!
                notifikasjon.deletedAt shouldBe null
            }

            it ("beskjed er slettet") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid5)
                notifikasjon shouldBe null
            }
        }
    }

    describe("softDeleteSakByGrupperingsid-oppførsel") {

        context("Eksisterende oppgave blir markert som slettet") {
            val (produsentModel, kafkaProducer, engine) = setupEngine()

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
                kafkaProducer.hendelser.removeLast().also {
                    it shouldBe instanceOf<SoftDelete>()
                }
            }

            it("har blitt slettet i modellen") {
                val notifikasjon = produsentModel.hentSak(uuid)
                notifikasjon shouldBe null
            }
            it("oppgave 2 har ikke fått slettet tidspunkt") {
                val notifikasjon = produsentModel.hentSak(uuid2)!!
                notifikasjon.deletedAt shouldBe null
            }
        }

        context("Oppgave mangler") {
            val (_, _, engine) = setupEngine()
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
            val (produsentModel, _, engine) = setupEngine()

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
            val (produsentModel, _, engine) = setupEngine()
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
