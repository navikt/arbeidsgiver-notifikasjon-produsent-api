package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.QueryMineNotifikasjoner
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class NyOppgaveFlereMottakereTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(relaxed = true),
            produsentRepository = produsentRepository,
        )
    )

    describe("sender ingen mottakere") {
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
                QueryMineNotifikasjoner.AltinnMottaker(
                    serviceCode = "5441",
                    serviceEdition = "1",
                    virksomhetsnummer = "0"
                )
            )
        }
    }

    describe("sender 1 mottaker i 'mottakere'") {
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
                QueryMineNotifikasjoner.AltinnMottaker(
                    serviceCode = "5441",
                    serviceEdition = "1",
                    virksomhetsnummer = "0"
                )
            )
        }
    }
    describe("sender 2 mottakere i 'mottakere'") {
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
                QueryMineNotifikasjoner.AltinnMottaker(
                    serviceCode = "5441",
                    serviceEdition = "1",
                    virksomhetsnummer = "0"
                ),
                QueryMineNotifikasjoner.NærmesteLederMottaker(
                    ansattFnr = "3",
                    naermesteLederFnr = "2",
                    virksomhetsnummer = "0"
                )
            )
        }
    }

    describe("sender 1 altinnRolle i 'mottaker'") {
        listOf(AltinnRolle("196", "DAGL"), AltinnRolle("195", "BOBE")).forEach {
            produsentRepository.leggTilAltinnRolle(
                it
            )
        }

        val response = engine.produsentApi(
            nyOppgave(
                """
            mottaker: {
                altinnRolle: {
                    roleDefinitionCode: "DAGL"                    
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
                QueryMineNotifikasjoner.AltinnRolleMottaker(
                    roleDefinitionCode = "DAGL",
                    roleDefinitionId = "196"
                )
            )
        }
    }

    describe("sender 2 mottaker, en i 'mottaker' og en i 'mottakere'") {
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
                QueryMineNotifikasjoner.AltinnMottaker(
                    serviceCode = "5441",
                    serviceEdition = "1",
                    virksomhetsnummer = "0"
                ),
                QueryMineNotifikasjoner.NærmesteLederMottaker(
                    ansattFnr = "3",
                    naermesteLederFnr = "2",
                    virksomhetsnummer = "0"
                )
            )
        }
    }
})

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
