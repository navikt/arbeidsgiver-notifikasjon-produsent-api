package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NyBeskjedFlereMottakereTest {

    @Test
    fun `sender ingen mottakere`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val response = client.produsentApi(
                nyBeskjed(
                    ""
                )
            )
            // response should have error
            val errors = response.getGraphqlErrors()
            assertFalse(errors.isEmpty())

        }
    }

    @Test
    fun `sender 1 mottaker i 'mottaker'`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val response = client.produsentApi(
                nyBeskjed(
                    """
            mottaker: {
                altinn: {
                    serviceCode: "5441"
                    serviceEdition: "1"
                }
            }
        """
                )
            )
            // no error in response
            assertTrue(response.getGraphqlErrors().isEmpty())

            // en mottaker registrert
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            assertEquals("NyBeskjedVellykket", resultType)

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
            val mottakere = client.hentMottakere(id)
            assertEquals(
                setOf(
                    QueryNotifikasjoner.AltinnMottaker(
                        serviceCode = "5441",
                        serviceEdition = "1",
                        virksomhetsnummer = "0"
                    )
                ), mottakere.toSet()
            )
        }
    }


    @Test
    fun `sender 1 mottaker i 'mottakere'`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val response = client.produsentApi(
                nyBeskjed(
                    """
            mottakere: [
                {
                    altinn: {
                        serviceCode: "5441"
                        serviceEdition: "1"
                    }
                }
            ]
        """
                )
            )
            // no error in response
            assertTrue(response.getGraphqlErrors().isEmpty())

            // en mottaker registrert
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            assertEquals("NyBeskjedVellykket", resultType)

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
            val mottakere = client.hentMottakere(id)
            assertEquals(
                setOf(
                    QueryNotifikasjoner.AltinnMottaker(
                        serviceCode = "5441",
                        serviceEdition = "1",
                        virksomhetsnummer = "0"
                    )
                ), mottakere.toSet()
            )
        }
    }

    @Test
    fun `sender 3 mottakere i 'mottakere'`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val response = client.produsentApi(
                nyBeskjed(
                    """
            mottakere: [
                {
                    altinn: {
                        serviceCode: "5441"
                        serviceEdition: "1"
                    }
                },
                {
                    naermesteLeder: {
                        naermesteLederFnr: "2"
                        ansattFnr: "3"
                    }
                },
                {
                    altinnRessurs: {
                        ressursId: "test-fager"
                    }
                }
            ]
        """
                )
            )
            // no errors in response
            assertTrue(response.getGraphqlErrors().isEmpty())

            // alle mottakere registreres
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            assertEquals("NyBeskjedVellykket", resultType)

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
            val mottakere = client.hentMottakere(id)
            assertEquals(
                setOf(
                    QueryNotifikasjoner.AltinnMottaker(
                        serviceCode = "5441",
                        serviceEdition = "1",
                        virksomhetsnummer = "0"
                    ),
                    QueryNotifikasjoner.NærmesteLederMottaker(
                        ansattFnr = "3",
                        naermesteLederFnr = "2",
                        virksomhetsnummer = "0"
                    ),
                    QueryNotifikasjoner.AltinnRessursMottaker(ressursId = "test-fager", virksomhetsnummer = "0")
                ), mottakere.toSet()
            )
            // sender hendelse med korrekt mottakere til kafka
            kafkaProducer.hendelser.filterIsInstance<HendelseModel.BeskjedOpprettet>().first().let {
                assertEquals(3, it.mottakere.size)
                assertEquals(1, it.mottakere.filterIsInstance<HendelseModel.AltinnMottaker>().size)
                assertEquals(1, it.mottakere.filterIsInstance<HendelseModel.NærmesteLederMottaker>().size)
                assertEquals(1, it.mottakere.filterIsInstance<HendelseModel.AltinnRessursMottaker>().size)
            }
        }
    }

    @Test
    fun `sender 2 mottaker, en i 'mottaker' og en i 'mottakere'`() =
        withTestDatabase(Produsent.databaseConfig) { database ->
            ktorProdusentTestServer(
                produsentRepository = ProdusentRepositoryImpl(database),
            ) {
                val response = client.produsentApi(
                    nyBeskjed(
                        """
                        mottaker: {
                            altinn: {
                                serviceCode: "5441"
                                serviceEdition: "1"
                            }
                        }
                        mottakere: [
                            {
                                naermesteLeder: {
                                    naermesteLederFnr: "2"
                                    ansattFnr: "3"
                                }
                            }
                        ]
                    """
                    )
                )
                // no errors in response
                assertTrue(response.getGraphqlErrors().isEmpty())
                // en mottaker registrert
                val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
                assertEquals("NyBeskjedVellykket", resultType)

                val id = response.getTypedContent<UUID>("/nyBeskjed/id")
                val mottakere = client.hentMottakere(id)
                assertEquals(
                    setOf(
                        QueryNotifikasjoner.AltinnMottaker(
                            serviceCode = "5441",
                            serviceEdition = "1",
                            virksomhetsnummer = "0"
                        ),
                        QueryNotifikasjoner.NærmesteLederMottaker(
                            ansattFnr = "3",
                            naermesteLederFnr = "2",
                            virksomhetsnummer = "0"
                        )
                    ), mottakere.toSet()
                )
            }
        }
}

fun nyBeskjed(fragment: String) = """
            mutation {
                nyBeskjed(nyBeskjed: {
                    $fragment
                    metadata: {
                        eksternId: "0"
                        virksomhetsnummer: "0"
                    }
                    notifikasjon: {
                        lenke: ""
                        tekst: ""
                        merkelapp: "tag"
                    }
                }) {
                    __typename
                    ... on NyBeskjedVellykket {
                        id
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            
            }
        """

internal suspend fun HttpClient.hentMottakere(id: UUID): List<QueryNotifikasjoner.Mottaker> {
    return this.produsentApi(
        """
        query {
            mineNotifikasjoner(merkelapp: "tag") {
                ... on NotifikasjonConnection {
                    edges {
                        node {
                            __typename
                            ... on Beskjed {
                                metadata {
                                    id
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
                                    ... on AltinnRessursMottaker {
                                        ressursId
                                        virksomhetsnummer
                                    }
                                }
                            }
                            ... on Oppgave {
                                metadata {
                                    id
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
                                    ... on AltinnRessursMottaker {
                                        ressursId
                                        virksomhetsnummer
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    """
    )
        .getTypedContent<List<JsonNode>>("$.mineNotifikasjoner.edges[*].node")
        .flatMap {
            if (it["metadata"]["id"].asText() == id.toString())
                laxObjectMapper.convertValue<List<QueryNotifikasjoner.Mottaker>>(it["mottakere"])
            else
                listOf()
        }
}