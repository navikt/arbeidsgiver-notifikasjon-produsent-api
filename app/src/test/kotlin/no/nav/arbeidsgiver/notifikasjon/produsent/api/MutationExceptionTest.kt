package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationExceptionTest {
    val ex = RuntimeException("woops!")

    //language=GraphQL
    private val gyldigeMutations = listOf(
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
                    tilleggsinformasjon: "Her er noe tilleggsinformasjon"
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

    @Test
    fun `robusthet ved intern feil`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
            kafkaProducer = object : FakeHendelseProdusent() {
                override suspend fun sendOgHentMetadata(hendelse: HendelseModel.Hendelse) = throw ex
            },
        ) {
            gyldigeMutations.forEach { query ->
                val response = client.produsentApi(query)
                assertEquals(1, response.getGraphqlErrors().size)
                assertTrue(response.getGraphqlErrors().first().message.contains(ex.message!!))
            }
        }
    }
}

