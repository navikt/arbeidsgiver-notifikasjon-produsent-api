package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MineNotifikasjonerTest {

    private val virksomhetsnummer = "123"
    private val merkelapp = "tag"
    private val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    private val grupperingsid = "sak1"

    private fun Database.produsentRepo() = ProdusentRepositoryImpl(this).apply {
        runBlocking {
            oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    merkelapp = merkelapp,
                    eksternId = "1",
                    mottakere = listOf(mottaker),
                    hendelseId = uuid("1"),
                    notifikasjonId = uuid("1"),
                    tekst = "test",
                    lenke = "https://nav.no",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                    grupperingsid = grupperingsid,
                    kildeAppNavn = "",
                    produsentId = "",
                    eksterneVarsler = listOf(),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )
            oppdaterModellEtterHendelse(
                BeskjedOpprettet(
                    virksomhetsnummer = "1",
                    merkelapp = merkelapp,
                    eksternId = "2",
                    mottakere = listOf(mottaker),
                    hendelseId = uuid("2"),
                    notifikasjonId = uuid("2"),
                    tekst = "test",
                    lenke = "https://nav.no",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                    grupperingsid = grupperingsid,
                    kildeAppNavn = "",
                    produsentId = "",
                    eksterneVarsler = listOf(),
                    hardDelete = null,
                    sakId = null,
                )
            )
            oppdaterModellEtterHendelse(
                BeskjedOpprettet(
                    virksomhetsnummer = "1",
                    merkelapp = merkelapp + "noe annet",
                    eksternId = "3",
                    mottakere = listOf(mottaker),
                    hendelseId = uuid("3"),
                    notifikasjonId = uuid("3"),
                    tekst = "test",
                    lenke = "https://nav.no",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                    grupperingsid = grupperingsid,
                    produsentId = "",
                    kildeAppNavn = "",
                    eksterneVarsler = listOf(),
                    hardDelete = null,
                    sakId = null,
                )
            )
            oppdaterModellEtterHendelse(
                BeskjedOpprettet(
                    virksomhetsnummer = "1",
                    merkelapp = merkelapp + "2",
                    eksternId = "3",
                    mottakere = listOf(mottaker),
                    hendelseId = uuid("4"),
                    notifikasjonId = uuid("4"),
                    tekst = "test",
                    lenke = "https://nav.no",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                    grupperingsid = grupperingsid,
                    produsentId = "",
                    kildeAppNavn = "",
                    eksterneVarsler = listOf(),
                    hardDelete = null,
                    sakId = null,
                )
            )
        }
    }

    @Test
    fun `produsent mangler tilgang til merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(merkelapp: "foo") {
                            __typename
                            ... on UgyldigMerkelapp {
                                feilmelding
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                getTypedContent<Error.UgyldigMerkelapp>("mineNotifikasjoner")
            }
        }
    }

    @Test
    fun `henter alle med default paginering`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(merkelapp: "tag") {
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
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                getTypedContent<QueryNotifikasjoner.NotifikasjonConnection>("mineNotifikasjoner")
            }


            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(grupperingsid: "sak1") {
                            __typename
                            ... on NotifikasjonConnection {
                                __typename
                                pageInfo {
                                    hasNextPage
                                    endCursor
                                }
                                edges {
                                    cursor
                                }
                                
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                assertEquals(3, getTypedContent<List<Any>>("mineNotifikasjoner/edges").size)
                assertEquals(false, getTypedContent<Boolean>("mineNotifikasjoner/pageInfo/hasNextPage"))
            }
        }
    }

    @Test
    fun `henter alle med angitt paginering`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            val connection = with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(merkelapp: "tag", first: 1) {
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
                """.trimIndent()
                )
            ) {
                val connection = getTypedContent<QueryNotifikasjoner.NotifikasjonConnection>("mineNotifikasjoner")

                // respons inneholder forventet data
                assertEquals(1, connection.edges.size)
                assertEquals(true, connection.pageInfo.hasNextPage)

                connection
            }

            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(merkelapp: "tag", after: "${connection.pageInfo.endCursor}") {
                            __typename
                            ... on NotifikasjonConnection {
                                __typename
                                pageInfo {
                                    hasNextPage
                                    endCursor
                                }
                                edges {
                                    cursor
                                }
                                
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                assertEquals(1, getTypedContent<List<Any>>("mineNotifikasjoner/edges").size)
                assertEquals(false, getTypedContent<Boolean>("mineNotifikasjoner/pageInfo/hasNextPage"))
            }
        }
    }

    @Test
    fun `når merkelapper= i filter`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(merkelapper: []) {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                val edges = getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                assertEquals(0, edges.size)
            }
        }
    }

    @Test
    fun `når merkelapp(er) ikke er angitt i filter`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                val edges = getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                assertEquals(3, edges.size)
            }
        }
    }

    @Test
    fun `når merkelapper er angitt i filter`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(
                            merkelapper: ["tag"]
                        ) {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                val edges = getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                assertEquals(2, edges.size)
            }
        }
    }

    @Test
    fun `når forskjellige merkelapper er angitt i filter`() =
        withTestDatabase(Produsent.databaseConfig) { database ->
            ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
                with(
                    client.produsentApi(
                        """
                    query {
                        mineNotifikasjoner(
                            merkelapper: ["tag" "tag2"]
                        ) {
                            __typename
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
                    )
                ) {
                    // respons inneholder forventet data
                    val edges = getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                    assertEquals(3, edges.size)
                }
            }
        }

    @Test
    fun `når grupperingsid er angitt i filter`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(merkelapp: "tag", grupperingsid: "$grupperingsid") {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                val edges = getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                assertEquals(2, edges.size)
            }
        }
    }


    @Test
    fun `når feil grupperingsid er angitt i filter`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(produsentRepository = database.produsentRepo()) {
            with(
                client.produsentApi(
                    """
                    query {
                        mineNotifikasjoner(merkelapp: "tag", grupperingsid: "bogus") {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
                )
            ) {
                // respons inneholder forventet data
                val edges = getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                assertEquals(0, edges.size)
            }
        }
    }
}

