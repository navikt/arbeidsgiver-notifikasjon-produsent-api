package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OppgavePaaminnelseEndresTest {

    private val virksomhetsnummer = "123"
    private val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    private val merkelapp = "tag"
    private val eksternId = "123"
    private val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )

    private val oppgaveOpprettetTidspunkt = OffsetDateTime.now()
    private val konkretPaaminnelsesTidspunkt = oppgaveOpprettetTidspunkt.toLocalDateTime().plusWeeks(1)

    @Test
    fun `Oppgave har ingen påminnelse men får ny`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
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
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
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
            }

            val response = client.produsentApi(
                """
                mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid",
                        paaminnelse: {
                            eksterneVarsler:[],
                            tidspunkt: {konkret: "$konkretPaaminnelsesTidspunkt"}
                        }
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            // returnerer tilbake oppgave id-en
            val vellykket =
                response.getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
            assertEquals(uuid, vellykket.id)
        }

        // har sendt melding til kafka med korrekt påminnelse
        val hendelse = kafkaProducer.hendelser
            .filterIsInstance<HendelseModel.OppgavePåminnelseEndret>()
            .last()
        assertEquals(
            HendelseModel.PåminnelseTidspunkt.Konkret(
                konkretPaaminnelsesTidspunkt,
                konkretPaaminnelsesTidspunkt.inOsloAsInstant()
            ), hendelse.påminnelse?.tidspunkt
        )
    }


    @Test
    fun `Oppgave får fjernet påminnelse`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
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
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            val response = client.produsentApi(
                """
                mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid", 
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            // returnerer tilbake oppgave id-en
            val vellykket =
                response.getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
            assertEquals(uuid, vellykket.id)

            // har sendt melding til kafka med tom påminnelse
            val hendelse = kafkaProducer.hendelser
                .filterIsInstance<HendelseModel.OppgavePåminnelseEndret>()
                .last()
            assertEquals(null, hendelse.påminnelse)
        }
    }

    @Test
    fun `Oppgave finnes ikke`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            val response = client.produsentApi(
                """
                mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid", 
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            // returnerer oppgave finnes ikke
            val finnesIkke = response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveEndrePaaminnelse")
            assertNotNull(finnesIkke.feilmelding)

            // melding er ikke sendt på kafka
            assertEquals(emptyList(), kafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>())
        }
    }

    @Test
    fun `Påminnelsestidspunkt er før oppgaven er opprettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }


            // Påminnelsestidspunkt er før oppgaveOpprettetTidspunkt
            val response = client.produsentApi(
                """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {konkret: "${oppgaveOpprettetTidspunkt.toLocalDateTime().minusDays(1)}"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
            )
            val ugyldigPåminnelseTidspunkt =
                response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
            assertNotNull(ugyldigPåminnelseTidspunkt.feilmelding)


            // melding er ikke sendt på kafka
            assertEquals(emptyList(), kafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>())
        }
    }

    @Test
    fun `Påminnelsestidspunkt er relativ til frist, men oppgaven har ingen frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            // Påminnelsestidspunkt er relativ til frist, men oppgave har ingen frist
            val response = client.produsentApi(
                """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {foerFrist: "P2DT3H4M"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
            )
            val ugyldigPåminnelseTidspunkt =
                response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
            assertNotNull(ugyldigPåminnelseTidspunkt.feilmelding)

            // melding er ikke sendt på kafka
            assertEquals(emptyList(), kafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>())
        }
    }

    @Test
    fun `Oppgaven har en frist, men konkret påminnelsestidspunkt er etter frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            val oppgaveFrist = oppgaveOpprettetTidspunkt.plusWeeks(1)

            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = oppgaveFrist.toLocalDate(),
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            // Påminnelsestidspunkt er etter oppgavens frist
            val response = client.produsentApi(
                """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {konkret: "${oppgaveFrist.plusDays(1).toLocalDateTime()}"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
            )
            val ugyldigPåminnelseTidspunkt =
                response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
            assertNotNull(ugyldigPåminnelseTidspunkt.feilmelding)

            // melding er ikke sendt på kafka
            assertEquals(emptyList(), kafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>())
        }
    }

    @Test
    fun `Relativ til starttidspunkt`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            val oppgaveFrist = oppgaveOpprettetTidspunkt.plusWeeks(1)

            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = oppgaveFrist.toLocalDate(),
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            // Påminnelsestidspunkt er relativ til starttidspunkt, men notifikasjon er en oppgave (ikke kalenderavtale)
            val response = client.produsentApi(
                """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {foerStartTidspunkt: "P2DT3H4M"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
            )
            val ugyldigPåminnelseTidspunkt =
                response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
            assertNotNull(ugyldigPåminnelseTidspunkt.feilmelding)

            // melding er ikke sendt på kafka
            assertEquals(emptyList(), kafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>())
        }
    }

    @Test
    fun `Identisk Idepotency Key`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            val oppgaveFrist = oppgaveOpprettetTidspunkt.plusWeeks(1)

            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = oppgaveFrist.toLocalDate(),
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,

                ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            // Mutation med identisk idepotency key kalles to ganger, men kun en melding sendes på kafka
            suspend fun kallOppgaveEndrePaaminnelse() = client.produsentApi(
                """
                    mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid",
                        paaminnelse: {
                            eksterneVarsler:[],
                            tidspunkt: {konkret: "$konkretPaaminnelsesTidspunkt"}
                        }
                        idempotencyKey: "1234"
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            val vellykket1 =
                kallOppgaveEndrePaaminnelse().getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>(
                    "oppgaveEndrePaaminnelse"
                )
            assertEquals(uuid, vellykket1.id)

            val vellykket2 =
                kallOppgaveEndrePaaminnelse().getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>(
                    "oppgaveEndrePaaminnelse"
                )
            assertEquals(uuid, vellykket2.id)

            assertEquals(1, kafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>().count())
        }
    }

    @Test
    fun `Påminnelse blir endret relativ til op`() = withTestDatabase(Produsent.databaseConfig) { database ->

        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
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
                sakId = null
            ).also { produsentModel.oppdaterModellEtterHendelse(it) }

            val response = client.produsentApi(
                """
                        mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                tidspunkt: {
                                    etterOpprettelse: "P14DT"
                                }
                            }
                            idempotencyKey: "1234"
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
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
                response.getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
            assertEquals(uuid, vellykket.id)

            // har sendt melding til kafka
            val hendelse = kafkaProducer.hendelser
                .filterIsInstance<HendelseModel.OppgavePåminnelseEndret>()
                .last()
            assertEquals(
                OffsetDateTime.parse("2024-01-15T01:01:00Z").toInstant(),
                hendelse.påminnelse?.tidspunkt?.påminnelseTidspunkt
            )
        }
    }
}

