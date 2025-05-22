package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.JsonNode
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

class HardDeleteNotifikasjonTest {

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
    fun `HardDelete-oppførsel Eksisterende oppgave blir slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            with(client.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on HardDeleteNotifikasjonVellykket {
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
                val vellykket = getTypedContent<MutationDelete.HardDeleteNotifikasjonVellykket>("hardDeleteNotifikasjon")
                assertEquals(uuid, vellykket.id)
            }


            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as HardDelete
            }

            // har blitt fjernet fra modellen
            assertNull(produsentModel.hentNotifikasjon(uuid))

            // notifikasjon2 har ikke blitt fjernet fra modellen
            assertNotNull(produsentModel.hentNotifikasjon(uuid2))

            // mineNotifikasjoner rapporterer at notifikasjon ikke finnes
            with(client.produsentApi(
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
            )) {
                val slettetNotifikasjon = getTypedContent<JsonNode>("mineNotifikasjoner")["edges"]
                    .toList()
                    .map { it["node"] }
                    .find { it["id"]?.asText() == uuid.toString() }
                assertNull(slettetNotifikasjon)
            }
        }
    }

    @Test
    fun `HardDelete-oppførsel Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjon")
        }
    }

    @Test
    fun `HardDelete-oppførsel Oppgave med feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.UgyldigMerkelapp>("hardDeleteNotifikasjon")
        }
    }

    @Test
    fun `hardDeleteNotifikasjonByEksternId-oppførsel Eksisterende oppgave blir slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            kafkaProducer.clear()
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            with(client.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on HardDeleteNotifikasjonVellykket {
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
                val vellykket = getTypedContent<MutationDelete.HardDeleteNotifikasjonVellykket>("hardDeleteNotifikasjonByEksternId")
                assertEquals(uuid, vellykket.id)
            }


            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as HardDelete
            }

            // finnes ikke i modellen
            assertNull(produsentModel.hentNotifikasjon(uuid))
            // oppgave2 finnes fortsatt i modellen
            assertNotNull(produsentModel.hentNotifikasjon(uuid2))
        }
    }

    @Test
    fun `hardDeleteNotifikasjonByEksternId-oppførsel Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
        }
    }

    @Test
    fun `hardDeleteNotifikasjonByEksternId-oppførsel Oppgave med feil merkelapp men riktig eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
        }
    }

    @Test
    fun `hardDeleteNotifikasjonByEksternId-oppførsel Oppgave med feil eksternId men riktig merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
        }
    }
}

