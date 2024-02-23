package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class NyOppgaveFlereMottakereTests : DescribeSpec({

    describe("sender ingen mottakere") {
        val engine = setupEngine()
        val response = engine.produsentApi(
            nyOppgave(
                """
                """
            )
        )
        it("response should have error") {
            val errors = response.getGraphqlErrors()
            errors shouldNot beEmpty()
        }
    }

    describe("sender 1 mottaker i 'mottaker'") {
        val engine = setupEngine()
        val response = engine.produsentApi(
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
        it("no error in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }

        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyOppgave.__typename")
            resultType shouldBe "NyOppgaveVellykket"

            val id = response.getTypedContent<UUID>("/nyOppgave/id")
            val mottakere = engine.hentMottakere(id)
            mottakere.toSet() shouldBe setOf(
                QueryNotifikasjoner.AltinnMottaker(
                    serviceCode = "5441",
                    serviceEdition = "1",
                    virksomhetsnummer = "0"
                )
            )
        }
    }

    describe("sender 1 mottaker i 'mottakere'") {
        val engine = setupEngine()
        val response = engine.produsentApi(
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
        it("no error in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }
        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyOppgave.__typename")
            resultType shouldBe "NyOppgaveVellykket"

            val id = response.getTypedContent<UUID>("/nyOppgave/id")
            val mottakere = engine.hentMottakere(id)
            mottakere.toSet() shouldBe setOf(
                QueryNotifikasjoner.AltinnMottaker(
                    serviceCode = "5441",
                    serviceEdition = "1",
                    virksomhetsnummer = "0"
                )
            )
        }
    }
    describe("sender 2 mottakere i 'mottakere'") {
        val engine = setupEngine()
        val response = engine.produsentApi(
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
        it("no errors in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }
        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyOppgave.__typename")
            resultType shouldBe "NyOppgaveVellykket"

            val id = response.getTypedContent<UUID>("/nyOppgave/id")
            val mottakere = engine.hentMottakere(id)
            mottakere.toSet() shouldBe setOf(
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
            )
        }
    }

    describe("sender 2 mottaker, en i 'mottaker' og en i 'mottakere'") {
        val engine = setupEngine()
        val response = engine.produsentApi(
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
        it("no errors in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }
        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyOppgave.__typename")
            resultType shouldBe "NyOppgaveVellykket"

            val id = response.getTypedContent<UUID>("/nyOppgave/id")
            val mottakere = engine.hentMottakere(id)
            mottakere.toSet() shouldBe setOf(
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
            )
        }
    }
})

private fun DescribeSpec.setupEngine(): TestApplicationEngine {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val engine = ktorProdusentTestServer(
        produsentRepository = produsentRepository,
    )
    return engine
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
