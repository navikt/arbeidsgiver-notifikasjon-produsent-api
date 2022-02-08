package no.nav.arbeidsgiver.notifikasjon.produsent_api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.QueryMineNotifikasjoner
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class NyBeskjedFlereMottakereTests: DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(relaxed = true),
            produsentRepository = produsentRepository,
        )
    )

    describe("sender ingen mottakere") {
        val response = engine.produsentApi(nyBeskjed("""
        """))
        it("response should have error") {
            val errors = response.getGraphqlErrors()
            errors shouldNot beEmpty()
        }
    }

    describe("sender 1 mottaker i 'mottaker'") {
        val response = engine.produsentApi(nyBeskjed("""
            mottaker: {
                altinn: {
                    serviceCode: "5441"
                    serviceEdition: "1"
                }
            }
        """))
        it("no error in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }

        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            resultType shouldBe "NyBeskjedVellykket"

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
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
        val response = engine.produsentApi(nyBeskjed("""
            mottakere: [
                {
                    altinn: {
                        serviceCode: "5441"
                        serviceEdition: "1"
                    }
                }
            ]
        """))
        it("no error in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }
        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            resultType shouldBe "NyBeskjedVellykket"

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
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
        val response = engine.produsentApi(nyBeskjed("""
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
        """))
        it("no errors in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }
        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            resultType shouldBe "NyBeskjedVellykket"

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
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

    describe("sender 2 mottaker, en i 'mottaker' og en i 'mottakere'") {
        val response = engine.produsentApi(nyBeskjed("""
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
        """))
        it("no errors in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }
        it("en mottaker registrert") {
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            resultType shouldBe "NyBeskjedVellykket"

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
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

fun TestApplicationEngine.hentMottakere(id: UUID): List<QueryMineNotifikasjoner.Mottaker> {
    return this.produsentApi("""
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
                                       ... on AltinnRolleMottaker {
                                        roleDefinitionCode,
                                        roleDefinitionId
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
                                     ... on AltinnRolleMottaker {
                                        roleDefinitionCode,
                                        roleDefinitionId
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    """)
        .getTypedContent<List<JsonNode>>("$.mineNotifikasjoner.edges[*].node")
        .flatMap {
            if (it["metadata"]["id"].asText() == id.toString())
                objectMapper.convertValue<List<QueryMineNotifikasjoner.Mottaker>>(it["mottakere"])
            else
                listOf()
        }
}