package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.StubbedKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*


class OppgaveUtførtTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val stubbedKafkaProducer = StubbedKafkaProducer()

    val engine = ktorProdusentTestServer(
        kafkaProducer = stubbedKafkaProducer,
        produsentRepository = produsentModel
    )


    describe("OppgaveUtført-oppførsel") {
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

        context("Eksisterende oppgave blir utført") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoert(
                        id: "$uuid", 
                        hardDelete: {
                            nyTid: {
                                den: "2019-10-13T07:20:50.52"
                            }
                            strategi: OVERSKRIV
                        }) {
                        __typename
                        ... on OppgaveUtfoertVellykket {
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
                val vellykket = response.getTypedContent<MutationOppgaveUtfoert.OppgaveUtfoertVellykket>("oppgaveUtfoert")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<OppgaveUtført>()
                    .last()
                hendelse.hardDelete shouldNotBe null
            }

            it("har utført-status i modellen") {
                val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
                oppgave.tilstand shouldBe ProdusentModel.Oppgave.Tilstand.UTFOERT
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoert(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoert")
            }
        }

        context("Oppgave med feil merkelapp") {
            val oppgaveOpprettet = OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = "feil merkelapp",
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = uuid,
                notifikasjonId= uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = opprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoert(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.UgyldigMerkelapp>("oppgaveUtfoert")
            }
        }

        context("Er ikke oppgave, men beskjed") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoert(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoert")
            }
        }
    }

    describe("oppgaveUtfoertByEksternId-oppførsel") {
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

        context("Eksisterende oppgave blir utført") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId(
                        eksternId: "$eksternId" 
                        merkelapp: "$merkelapp"
                        hardDelete: {
                            nyTid: {
                                den: "2019-10-13T07:20:50.52"
                            }
                            strategi: OVERSKRIV
                        }
                    ) {
                        __typename
                        ... on OppgaveUtfoertVellykket {
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
                val vellykket = response.getTypedContent<MutationOppgaveUtfoert.OppgaveUtfoertVellykket>("oppgaveUtfoertByEksternId")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<OppgaveUtført>()
                    .last()
                hendelse.hardDelete shouldNotBe null
            }

            it("har utført-status i modellen") {
                val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
                oppgave.tilstand shouldBe ProdusentModel.Oppgave.Tilstand.UTFOERT
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }

        context("Oppgave med feil merkelapp men riktig eksternId") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }

        context("Oppgave med feil eksternId men riktig merkelapp") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }

        context("Er ikke oppgave, men beskjed") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }
    }

    describe("oppgaveUtfoertByEksternId_V2-oppførsel") {
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

        context("Eksisterende oppgave blir utført") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId_V2(
                        eksternId: "$eksternId" 
                        merkelapp: "$merkelapp"
                        hardDelete: {
                            nyTid: {
                                den: "2019-10-13T07:20:50.52"
                            }
                            strategi: OVERSKRIV
                        }
                    ) {
                        __typename
                        ... on OppgaveUtfoertVellykket {
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
                val vellykket = response.getTypedContent<MutationOppgaveUtfoert.OppgaveUtfoertVellykket>("oppgaveUtfoertByEksternId_V2")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<OppgaveUtført>()
                    .last()
                hendelse.hardDelete shouldNotBe null
            }

            it("har utført-status i modellen") {
                val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
                oppgave.tilstand shouldBe ProdusentModel.Oppgave.Tilstand.UTFOERT
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId_V2(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }

        context("Oppgave med feil merkelapp men riktig eksternId") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId_V2(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }

        context("Oppgave med feil eksternId men riktig merkelapp") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId_V2(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }

        context("Er ikke oppgave, men beskjed") {
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
            )

            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveUtfoertByEksternId_V2(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }
    }
})
