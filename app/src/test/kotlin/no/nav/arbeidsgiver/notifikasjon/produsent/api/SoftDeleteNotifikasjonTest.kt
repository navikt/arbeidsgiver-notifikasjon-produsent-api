package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
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
import kotlin.test.assertNull


class SoftDeleteNotifikasjonTest {

    private val virksomhetsnummer = "123"
    private val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    private val uuid2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
    private val merkelapp = "tag"
    private val eksternId = "123"
    private val eksternId2 = "234"
    private val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    private val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

    private val oppgaveOpprettet = OppgaveOpprettet(
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
    private val oppgaveOpprettet2 = oppgaveOpprettet.copy(
        eksternId = eksternId2,
        hendelseId = uuid2,
        notifikasjonId = uuid2,
    )


    @Test
    fun `SoftDelete Eksisterende oppgave blir slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            with(
                client.produsentApi(
                    """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on SoftDeleteNotifikasjonVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer tilbake id-en
                val vellykket =
                    getTypedContent<MutationDelete.SoftDeleteNotifikasjonVellykket>("softDeleteNotifikasjon")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as HardDelete
            }

            // har blitt slettet i modellen
            assertNull(produsentModel.hentNotifikasjon(uuid))

            // notifikasjon2 har ikke slettet-status i modellen
            produsentModel.hentNotifikasjon(uuid2).let {
                assertNotNull(it)
                assertNull(it.deletedAt)
            }

            // mineNotifikasjoner rapporterer ikke lenger notifikasjon
            with(
                client.produsentApi(
                    """
                        query {
                            mineNotifikasjoner(merkelapp: "$merkelapp") {
                            __typename
                            ... on NotifikasjonConnection {
                                __typename
                                pageInfo {
                                    hasNextPage
                                    endCursor
                                }
                                edges {
                                    cursor
                                    node {
                                      __typename
                                      ... on Beskjed {
                                        mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        mottakere {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid 
                                        }
                                        beskjed {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                      }
                                      ... on Oppgave {
                                      mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        mottakere {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid 
                                        }
                                        oppgave {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                            tilstand
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                      }
                                    }
                                }
                            }
                        }
                    }
                    """
                )
            ) {
                val slettetNotifikasjon =
                    getTypedContent<QueryNotifikasjoner.NotifikasjonConnection>("mineNotifikasjoner")
                        .edges
                        .map { it.node }
                        .find { it.id == uuid }

                assertNull(slettetNotifikasjon)
            }
        }
    }

    @Test
    fun `SoftDelete Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            with(
                client.produsentApi(
                    """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjon")
            }
        }
    }

    @Test
    fun `SoftDelete Oppgave med feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(merkelapp = "feil merkelapp"))

            with(
                client.produsentApi(
                    """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer feilmelding
                getTypedContent<Error.UgyldigMerkelapp>("softDeleteNotifikasjon")
            }
        }
    }

    @Test
    fun `SoftDeleteNotifikasjonByEksternId Eksisterende oppgave blir markert som slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            with(
                client.produsentApi(
                    """
                mutation {
                    softDeleteNotifikasjonByEksternId_V2(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on SoftDeleteNotifikasjonVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer tilbake id-en
                val vellykket =
                    getTypedContent<MutationDelete.SoftDeleteNotifikasjonVellykket>("softDeleteNotifikasjonByEksternId_V2")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as HardDelete
            }

            // har blitt slettet i modellen
            assertNull(produsentModel.hentNotifikasjon(uuid))

            // oppgave 2 har ikke fått slettet tidspunkt
            produsentModel.hentNotifikasjon(uuid2).let {
                assertNotNull(it)
                assertNull(it.deletedAt)
            }
        }
    }

    @Test
    fun `SoftDeleteNotifikasjonByEksternId Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            with(
                @Suppress("GraphQLDeprecatedSymbols")
                client.produsentApi(
                    """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }
    }

    @Test
    fun `SoftDeleteNotifikasjonByEksternId Oppgave med feil merkelapp men riktig eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            @Suppress("GraphQLDeprecatedSymbols")
            with(
                client.produsentApi(
                    """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }
    }

    @Test
    fun `SoftDeleteNotifikasjonByEksternId Oppgave med feil eksternId men riktig merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            @Suppress("GraphQLDeprecatedSymbols")
            with(
                client.produsentApi(
                    """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer feilmelding
                getTypedContent<Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }
    }
}

