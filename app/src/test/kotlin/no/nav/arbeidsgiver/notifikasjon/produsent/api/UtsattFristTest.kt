package no.nav.arbeidsgiver.notifikasjon.produsent.api

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
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals


class UtsattFristTest {

    private val virksomhetsnummer = "123"
    private val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    private val merkelapp = "tag"
    private val eksternId = "123"
    private val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    private val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

    @Test
    fun `OppgaveUtsettFrist Utgått oppgave får utsatt frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
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


            val response = client.produsentApi(
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

            // returnerer tilbake id-en
            val vellykket =
                response.getTypedContent<MutationOppgaveUtsettFrist.OppgaveUtsettFristVellykket>("oppgaveUtsettFrist")
            assertEquals(uuid, vellykket.id)

            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<FristUtsatt>()
                .last()
            assertEquals(LocalDate.parse("2023-01-05"), hendelse.frist)

            // har ny-status i modellen
            val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
            assertEquals(ProdusentModel.Oppgave.Tilstand.NY, oppgave.tilstand)
        }
    }

    @Test
    fun `OppgaveUtsettFrist Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val response = client.produsentApi(
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

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFrist")
        }
    }

    @Test
    fun `OppgaveUtsettFrist Oppgave med feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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

            val response = client.produsentApi(
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

            // returnerer feilmelding
            response.getTypedContent<Error.UgyldigMerkelapp>("oppgaveUtsettFrist")
        }
    }

    @Test
    fun `OppgaveUtsettFrist Er ikke oppgave, men beskjed`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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

            val response = client.produsentApi(
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

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFrist")
        }
    }

    @Test
    fun `OppgaveUtsettFrist Oppgave med frist som er senere enn ny frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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


            val response = client.produsentApi(
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

            // Får feilmelding
            response.getTypedContent<Error.Konflikt>("oppgaveUtsettFrist")
        }
    }


    @Test
    fun `OppgaveUtsettFristByEksternId Utgått oppgave får utsatt frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
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


            val response = client.produsentApi(
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

            // returnerer tilbake id-en
            val vellykket =
                response.getTypedContent<MutationOppgaveUtsettFrist.OppgaveUtsettFristVellykket>("oppgaveUtsettFristByEksternId")
            assertEquals(uuid, vellykket.id)

            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<FristUtsatt>()
                .last()
            assertEquals(LocalDate.parse("2023-01-05"), hendelse.frist)

            // har ny-status i modellen
            val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
            assertEquals(ProdusentModel.Oppgave.Tilstand.NY, oppgave.tilstand)
        }
    }

    @Test
    fun `OppgaveUtsettFristByEksternId Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val response = client.produsentApi(
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

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
        }
    }

    @Test
    fun `OppgaveUtsettFristByEksternId Oppgave med feil merkelapp men riktig eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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

            val response = client.produsentApi(
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

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
        }
    }

    @Test
    fun `OppgaveUtsettFristByEksternId Oppgave med riktig merkelapp men feil eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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

            val response = client.produsentApi(
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

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
        }
    }

    @Test
    fun `OppgaveUtsettFristByEksternId Er ikke oppgave, men beskjed`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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

            val response = client.produsentApi(
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

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtsettFristByEksternId")
        }
    }

    @Test
    fun `OppgaveUtsettFristByEksternId Oppgave med frist som er senere enn ny frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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


            val response = client.produsentApi(
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

            // Får feilmelding
            response.getTypedContent<Error.Konflikt>("oppgaveUtsettFristByEksternId")
        }
    }

    @Test
    fun `OppgaveUtsettFristByEksternId Utgått Oppgave får utsatt frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = uuid,
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01:00Z"),
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
                        utgaattTidspunkt = OffsetDateTime.parse("2024-01-07T01:01Z")
                    )
                )
            }

            val response = client.produsentApi(
                """
            mutation {
                oppgaveUtsettFristByEksternId(
                    eksternId: "$eksternId", 
                    merkelapp: "$merkelapp",
                    nyFrist: "2024-01-21",
                    paaminnelse: {
                        tidspunkt: {
                            etterOpprettelse: "P14DT"
                        }
                    }
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
            // Påminnelsestidspunkt er satt relativ til opprettelse av oppgave
            val vellykket =
                response.getTypedContent<MutationOppgaveUtsettFrist.OppgaveUtsettFristVellykket>("oppgaveUtsettFristByEksternId")
            assertEquals(uuid, vellykket.id)

            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<FristUtsatt>()
                .last()
            assertEquals(LocalDate.parse("2024-01-21"), hendelse.frist)
            assertEquals(
                OffsetDateTime.parse("2024-01-15T01:01:00Z").toInstant(),
                hendelse.påminnelse?.tidspunkt?.påminnelseTidspunkt
            )
        }
    }
}


