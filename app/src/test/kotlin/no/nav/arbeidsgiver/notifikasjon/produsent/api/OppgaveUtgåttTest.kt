package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class OppgaveUtgåttTest {


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
    fun `OppgaveUtgått Eksisterende oppgave utgår`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
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

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaatt(
                        id: "$uuid", 
                        hardDelete: {
                            nyTid: {
                                den: "2019-10-13T07:20:50.52"
                            }
                            strategi: OVERSKRIV
                        }) {
                        __typename
                        ... on OppgaveUtgaattVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer tilbake id-en
                val vellykket =
                    getTypedContent<MutationOppgaveUtgaatt.OppgaveUtgaattVellykket>("oppgaveUtgaatt")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<OppgaveUtgått>()
                .last()
            assertNotNull(hendelse.hardDelete)

            // har utgått-status i modellen
            val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
            assertEquals(ProdusentModel.Oppgave.Tilstand.UTGAATT, oppgave.tilstand)
        }
    }

    @Test
    fun `OppgaveUtgått Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaatt(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtgaatt")
            }
        }
    }

    @Test
    fun `OppgaveUtgått Oppgave med feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
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

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaatt(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.UgyldigMerkelapp>("oppgaveUtgaatt")
            }
        }
    }

    @Test
    fun `OppgaveUtgått Er ikke oppgave, men beskjed`() = withTestDatabase(Produsent.databaseConfig) { database ->
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

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaatt(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtgaatt")
            }
        }
    }

    @Test
    fun `OppgaveUtgått Oppgave er allerede utført`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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
            val oppgaveUtført = HendelseModel.OppgaveUtført(
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                notifikasjonId = oppgaveOpprettet.notifikasjonId,
                hendelseId = UUID.fromString("113e3360-1911-4955-bc22-88ccca397211"),
                produsentId = oppgaveOpprettet.produsentId,
                kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
                hardDelete = null,
                nyLenke = null,
                utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveUtført)

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaatt(id: "$uuid") {
                        __typename
                        ... on OppgaveUtgaattVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.OppgavenErAlleredeUtfoert>("oppgaveUtgaatt")
            }
        }
    }

    @Test
    fun `OppgaveUtgaattByEksternId Eksisterende oppgave blir utgått`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
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

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaattByEksternId(
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
                        ... on OppgaveUtgaattVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer tilbake id-en
                val vellykket =
                    getTypedContent<MutationOppgaveUtgaatt.OppgaveUtgaattVellykket>("oppgaveUtgaattByEksternId")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<OppgaveUtgått>()
                .last()
            assertNotNull(hendelse.hardDelete)

            // har utgått-status i modellen
            val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
            assertEquals(ProdusentModel.Oppgave.Tilstand.UTGAATT, oppgave.tilstand)
        }
    }

    @Test
    fun `OppgaveUtgaattByEksternId Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaattByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtgaattByEksternId")
            }
        }
    }

    @Test
    fun `Oppgave med feil merkelapp men riktig eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaattByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtgaattByEksternId")
            }
        }
    }

    @Test
    fun `OppgaveUtgaattByEksternId Oppgave med feil eksternId men riktig merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaattByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtgaattByEksternId")
            }
        }
    }

    @Test
    fun `OppgaveUtgaattByEksternId Er ikke oppgave, men beskjed`() = withTestDatabase(Produsent.databaseConfig) { database ->
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

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaattByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtgaattByEksternId")
            }
        }
    }

    @Test
    fun `OppgaveUtgaattByEksternId Oppgave er allerede utført`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
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
            val oppgaveUtført = HendelseModel.OppgaveUtført(
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                notifikasjonId = oppgaveOpprettet.notifikasjonId,
                hendelseId = UUID.fromString("113e3360-1911-4955-bc22-88ccca397211"),
                produsentId = oppgaveOpprettet.produsentId,
                kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
                hardDelete = null,
                nyLenke = null,
                utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveUtført)

            with(client.produsentApi(
                """
                mutation {
                    oppgaveUtgaattByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.OppgavenErAlleredeUtfoert>("oppgaveUtgaattByEksternId")
            }
        }
    }
}

