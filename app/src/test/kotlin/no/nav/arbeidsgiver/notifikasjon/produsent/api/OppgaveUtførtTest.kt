package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
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


class OppgaveUtførtTest {

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
    fun `OppgaveUtført Eksisterende oppgave blir utført`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
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
            )) {
                // returnerer tilbake id-en
                val vellykket = getTypedContent<MutationOppgaveUtfoert.OppgaveUtfoertVellykket>("oppgaveUtfoert")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<OppgaveUtført>()
                .last()
            assertNotNull(hendelse.hardDelete)

            // har utført-status i modellen
            val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
            assertEquals(ProdusentModel.Oppgave.Tilstand.UTFOERT, oppgave.tilstand)
        }
    }

    @Test
    fun `OppgaveUtført Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
            with(client.produsentApi(
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
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoert")
            }

        }
    }

    @Test
    fun `OppgaveUtført Oppgave med feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
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
                    oppgaveUtfoert(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.UgyldigMerkelapp>("oppgaveUtfoert")
            }
        }
    }

    @Test
    fun `OppgaveUtført Er ikke oppgave, men beskjed`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
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
                    oppgaveUtfoert(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoert")
            }

        }
    }


    @Test
    fun `OppgaveUtfoertByEksternId Eksisterende oppgave blir utført`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
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

            @Suppress("GraphQLDeprecatedSymbols")
            with(client.produsentApi(
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
            )) {
                // returnerer tilbake id-en
                val vellykket =
                    getTypedContent<MutationOppgaveUtfoert.OppgaveUtfoertVellykket>("oppgaveUtfoertByEksternId")
                assertEquals(uuid, vellykket.id)
            }


            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<OppgaveUtført>()
                .last()
            assertNotNull(hendelse.hardDelete)

            // har utført-status i modellen
            val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
            assertEquals(ProdusentModel.Oppgave.Tilstand.UTFOERT, oppgave.tilstand)
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
            @Suppress("GraphQLDeprecatedSymbols")
            with(client.produsentApi(
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
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId Oppgave med feil merkelapp men riktig eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
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

            @Suppress("GraphQLDeprecatedSymbols")
            with(client.produsentApi(
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
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId Oppgave med feil eksternId men riktig merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
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

            @Suppress("GraphQLDeprecatedSymbols")
            with(client.produsentApi(
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
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId Er ikke oppgave, men beskjed`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
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

            @Suppress("GraphQLDeprecatedSymbols")
            with(client.produsentApi(
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
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId")
            }
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId_V2 Eksisterende oppgave blir utført`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
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
            )) {
                // returnerer tilbake id-en
                val vellykket =
                    getTypedContent<MutationOppgaveUtfoert.OppgaveUtfoertVellykket>("oppgaveUtfoertByEksternId_V2")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<OppgaveUtført>()
                .last()
            assertNotNull(hendelse.hardDelete)

            // har utført-status i modellen
            val oppgave = produsentModel.hentNotifikasjon(uuid) as ProdusentModel.Oppgave
            assertEquals(ProdusentModel.Oppgave.Tilstand.UTFOERT, oppgave.tilstand)
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId_V2 Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = produsentModel
        ) {
            with(client.produsentApi(
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
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId_V2 Oppgave med feil merkelapp men riktig eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
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
                    oppgaveUtfoertByEksternId_V2(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId_V2 Oppgave med feil eksternId men riktig merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
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
                    oppgaveUtfoertByEksternId_V2(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }
    }

    @Test
    fun `OppgaveUtfoertByEksternId_V2 Er ikke oppgave, men beskjed`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
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
                    oppgaveUtfoertByEksternId_V2(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveUtfoertByEksternId_V2")
            }
        }
    }
}

