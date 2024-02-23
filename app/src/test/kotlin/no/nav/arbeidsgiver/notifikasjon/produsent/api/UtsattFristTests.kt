package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*


class UtsattFristTests : DescribeSpec({

    describe("oppgaveUtsettFrist-oppførsel") {
        val virksomhetsnummer = "123"
        val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
        val merkelapp = "tag"
        val eksternId = "123"
        val mottaker = AltinnMottaker(
            virksomhetsnummer = virksomhetsnummer,
            serviceCode = "1",
            serviceEdition = "1"
        )
        val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

        context("Utgått oppgave får utsatt frist") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
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
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
                produsentModel.oppdaterModellEtterHendelse(
                    OppgaveUtgått(
                        virksomhetsnummer = it.virksomhetsnummer,
                        notifikasjonId = it.notifikasjonId,
                        hendelseId = it.hendelseId,
                        produsentId = it.produsentId,
                        kildeAppNavn = it.kildeAppNavn,
                        hardDelete = null,
                        nyLenke = null,
                        utgaattTidspunkt = OffsetDateTime.now()
                    )
                )
            }


            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFrist(
                        id: "$uuid", 
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on OppgaveUtsettFristVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer tilbake id-en") {
                val vellykket =
                    response.getTypedContent<MutationOppgaveUtsettFrist.OppgaveUtsettFristVellykket>("oppgaveUtsettFrist")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<FristUtsatt>()
                    .last()
                hendelse.frist shouldBe LocalDate.parse("2023-01-05")
            }

            it("har ny-status i modellen") {
                val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
                oppgave.tilstand shouldBe ProdusentModel.Oppgave.Tilstand.NY
            }
        }

        context("Oppgave mangler") {
            val (_, _, engine) = setupEngine()
            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFrist(
                        id: "$uuid", 
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFrist")
            }
        }

        context("Oppgave med feil merkelapp") {
            val (produsentModel, _, engine) = setupEngine()
            val oppgaveOpprettet = OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = "feil merkelapp",
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

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFrist(
                        id: "$uuid", 
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.UgyldigMerkelapp>("oppgaveUtsettFrist")
            }
        }

        context("Er ikke oppgave, men beskjed") {
            val (produsentModel, _, engine) = setupEngine()
            val beskjedOpprettet = BeskjedOpprettet(
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
                sakId = null,
            )

            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFrist(
                        id: "$uuid", 
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFrist")
            }
        }

        context("Oppgave med frist som er senere enn ny frist") {
            val (produsentModel, _, engine) = setupEngine()
            OppgaveOpprettet(
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
                frist = LocalDate.parse("2023-12-24"),
                påminnelse = null,
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }


            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFrist(
                        id: "$uuid", 
                        nyFrist: "2023-01-01"
                    ) {
                        __typename
                        ... on OppgaveUtsettFristVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("Får feilmelding") {
                response.getTypedContent<Error.Konflikt>("oppgaveUtsettFrist")
            }
        }

    }


    describe("oppgaveUtsettFristByEksternId-oppførsel") {
        val virksomhetsnummer = "123"
        val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
        val merkelapp = "tag"
        val eksternId = "123"
        val mottaker = AltinnMottaker(
            virksomhetsnummer = virksomhetsnummer,
            serviceCode = "1",
            serviceEdition = "1"
        )
        val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

        context("Utgått oppgave får utsatt frist") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
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
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
                produsentModel.oppdaterModellEtterHendelse(
                    OppgaveUtgått(
                        virksomhetsnummer = it.virksomhetsnummer,
                        notifikasjonId = it.notifikasjonId,
                        hendelseId = it.hendelseId,
                        produsentId = it.produsentId,
                        kildeAppNavn = it.kildeAppNavn,
                        hardDelete = null,
                        nyLenke = null,
                        utgaattTidspunkt = OffsetDateTime.now()
                    )
                )
            }


            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFristByEksternId(
                        eksternId: "$eksternId", 
                        merkelapp: "$merkelapp", 
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on OppgaveUtsettFristVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer tilbake id-en") {
                val vellykket =
                    response.getTypedContent<MutationOppgaveUtsettFrist.OppgaveUtsettFristVellykket>("oppgaveUtsettFristByEksternId")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<FristUtsatt>()
                    .last()
                hendelse.frist shouldBe LocalDate.parse("2023-01-05")
            }

            it("har ny-status i modellen") {
                val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
                oppgave.tilstand shouldBe ProdusentModel.Oppgave.Tilstand.NY
            }
        }

        context("Oppgave mangler") {
            val (_, _, engine) = setupEngine()
            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFristByEksternId(
                        eksternId: "$eksternId", 
                        merkelapp: "$merkelapp", 
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
            }
        }

        context("Oppgave med feil merkelapp men riktig eksternId") {
            val (produsentModel, _, engine) = setupEngine()
            val oppgaveOpprettet = OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = "feil merkelapp",
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

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFristByEksternId(
                        eksternId: "$eksternId", 
                        merkelapp: "nope$merkelapp",
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
            }
        }

        context("Oppgave med riktig merkelapp men feil eksternId") {
            val (produsentModel, _, engine) = setupEngine()
            val oppgaveOpprettet = OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = "feil merkelapp",
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

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFristByEksternId(
                        eksternId: "nope$eksternId", 
                        merkelapp: "$merkelapp",
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
            }
        }

        context("Er ikke oppgave, men beskjed") {
            val (produsentModel, _, engine) = setupEngine()
            val beskjedOpprettet = BeskjedOpprettet(
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
                sakId = null,
            )

            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFristByEksternId(
                        eksternId: "$eksternId", 
                        merkelapp: "$merkelapp",
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
            }
        }

        context("Oppgave med frist som er senere enn ny frist") {
            val (produsentModel, _, engine) = setupEngine()
            OppgaveOpprettet(
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
                frist = LocalDate.parse("2023-12-24"),
                påminnelse = null,
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }


            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtsettFristByEksternId(
                        eksternId: "$eksternId", 
                        merkelapp: "$merkelapp",
                        nyFrist: "2023-01-05"
                    ) {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("Får feilmelding") {
                response.getTypedContent<Error.Konflikt>("oppgaveUtsettFristByEksternId")
            }
        }
    }
})

private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val stubbedKafkaProducer = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = stubbedKafkaProducer,
        produsentRepository = produsentModel
    )
    return Triple(produsentModel, stubbedKafkaProducer, engine)
}
