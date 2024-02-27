package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContainIgnoringCase
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class MutationExceptionTests : DescribeSpec({
    val ex = RuntimeException("woops!")

    //language=GraphQL
    val gyldigeMutations = listOf(
        """mutation {
            nyBeskjed(nyBeskjed: {
                mottaker: {
                    naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    } 
                }
                notifikasjon: {
                    lenke: "https://foo.bar",
                    tekst: "hello world",
                    merkelapp: "tag",
                }
                metadata: {
                    eksternId: "${UUID.randomUUID()}",
                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                    virksomhetsnummer: "42"
                    hardDelete: {
                      den: "2019-10-13T07:20:50.52"
                    }
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
        }""",

        """mutation { 
            nyOppgave(nyOppgave: {
                mottaker: {
                    naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    } 
                }
                notifikasjon: {
                    lenke: "https://foo.bar",
                    tekst: "hello world",
                    merkelapp: "tag",
                }
                metadata: {
                    eksternId: "${UUID.randomUUID()}",
                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                    virksomhetsnummer: "42"
                    hardDelete: {
                      den: "2019-10-13T07:20:50.52"
                    }
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
        }""",

        """mutation {
                nySak(
                    virksomhetsnummer: "1"
                    merkelapp: "tag"
                    grupperingsid: "42"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    initiellStatus: MOTTATT
                    tidspunkt: "2020-01-01T01:01Z"
                    tittel: "foo"
                    lenke: "https://foo.no"
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                    
                    ... on Error {
                        feilmelding
                    }
                }
        }""",
    )

    describe("robusthet ved intern feil") {
        val database = testDatabase(Produsent.databaseConfig)
        val produsentRepository = ProdusentRepositoryImpl(database)
        val engine = ktorProdusentTestServer(
            produsentRepository = produsentRepository,
            kafkaProducer = object : FakeHendelseProdusent() {
                override suspend fun sendOgHentMetadata(hendelse: HendelseModel.Hendelse) = throw ex
            },
        )
        withData(gyldigeMutations) { query ->
            val response = engine.produsentApi(query)
            response.getGraphqlErrors() shouldHaveSize 1
            response.getGraphqlErrors().first().message shouldContainIgnoringCase ex.message!!
        }
    }
})

