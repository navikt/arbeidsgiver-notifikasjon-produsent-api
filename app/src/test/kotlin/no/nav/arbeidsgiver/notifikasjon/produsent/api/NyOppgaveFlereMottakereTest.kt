package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NyOppgaveFlereMottakereTest {

    @Test
    fun `sender ingen mottakere`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(client.produsentApi(nyOppgave(""))) {
                // response should have error
                assertFalse(getGraphqlErrors().isEmpty())
            }
        }
    }

    @Test
    fun `sender 1 mottaker i 'mottaker'`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
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
            ) {

                // no error in response
                assertTrue(getGraphqlErrors().isEmpty())

                // en mottaker registrert
                val resultType = getTypedContent<String>("$.nyOppgave.__typename")
                assertEquals("NyOppgaveVellykket", resultType)

                val id = getTypedContent<UUID>("/nyOppgave/id")
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
    }

    @Test
    fun `sender 1 mottaker i 'mottakere'`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
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
            ) {
                // no error in response
                assertTrue(getGraphqlErrors().isEmpty())
                // en mottaker registrert
                val resultType = getTypedContent<String>("$.nyOppgave.__typename")
                assertEquals("NyOppgaveVellykket", resultType)

                val id = getTypedContent<UUID>("/nyOppgave/id")
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
    }

    @Test
    fun `sender 2 mottakere i 'mottakere'`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
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
                        }
                    ]
                    """
                    )
                )
            ) {
                // no errors in response
                assertTrue(getGraphqlErrors().isEmpty())
                // en mottaker registrert
                val resultType = getTypedContent<String>("$.nyOppgave.__typename")
                assertEquals("NyOppgaveVellykket", resultType)

                val id = getTypedContent<UUID>("/nyOppgave/id")
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

    @Test
    fun `sender 2 mottaker, en i 'mottaker' og en i 'mottakere'`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
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
            ) {
                // no errors in response
                assertTrue(getGraphqlErrors().isEmpty())
                // en mottaker registrert
                val resultType = getTypedContent<String>("$.nyOppgave.__typename")
                assertEquals("NyOppgaveVellykket", resultType)

                val id = getTypedContent<UUID>("/nyOppgave/id")
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
}

private fun nyOppgave(fragment: String) = """
            mutation {
                nyOppgave(nyOppgave: {
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
                    ... on NyOppgaveVellykket {
                        id
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            
            }
        """
