package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*

class NyBeskjedFlereMottakereTests : DescribeSpec({

    describe("sender ingen mottakere") {
        val engine = setupEngine()
        val response = engine.produsentApi(
            nyBeskjed(
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
                QueryNotifikasjoner.AltinnMottaker(
                    serviceCode = "5441",
                    serviceEdition = "1",
                    virksomhetsnummer = "0"
                )
            )
        }
    }
    describe("sender 3 mottakere i 'mottakere'") {
        val kafkaProducer = FakeHendelseProdusent()
        val engine = setupEngine(kafkaProducer)
        val response = engine.produsentApi(
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
        it("no errors in response") {
            val errors = response.getGraphqlErrors()
            errors.shouldBeEmpty()
        }
        it("alle mottakere registreres") {
            val resultType = response.getTypedContent<String>("$.nyBeskjed.__typename")
            resultType shouldBe "NyBeskjedVellykket"

            val id = response.getTypedContent<UUID>("/nyBeskjed/id")
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
                ),
                QueryNotifikasjoner.AltinnRessursMottaker(
                    ressursId = "test-fager",
                    virksomhetsnummer = "0"
                )
            )
        }
        it("sender hendelse med korrekt mottakere til kafka") {
            kafkaProducer.hendelser.filterIsInstance<HendelseModel.BeskjedOpprettet>().first().let {
                it.mottakere.size shouldBe  3
                it.mottakere.filterIsInstance<HendelseModel.AltinnMottaker>().size shouldBe 1
                it.mottakere.filterIsInstance<HendelseModel.NærmesteLederMottaker>().size shouldBe 1
                it.mottakere.filterIsInstance<HendelseModel.AltinnRessursMottaker>().size shouldBe 1
            }
        }
    }

    describe("sender 2 mottaker, en i 'mottaker' og en i 'mottakere'") {
        val engine = setupEngine()
        val response = engine.produsentApi(
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

private fun DescribeSpec.setupEngine(kafkaProducer: HendelseProdusent = NoopHendelseProdusent): TestApplicationEngine {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentRepository,
    )
    return engine
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

internal fun TestApplicationEngine.hentMottakere(id: UUID): List<QueryNotifikasjoner.Mottaker> {
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