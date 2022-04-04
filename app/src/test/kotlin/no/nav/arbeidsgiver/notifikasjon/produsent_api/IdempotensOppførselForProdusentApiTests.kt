package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.Error
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationNyBeskjed
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationNyOppgave
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*

class IdempotensOppførselForProdusentApiTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val queryModel = ProdusentRepositoryImpl(database)

    val virksomhetsnummer = "1234"
    val mottaker = AltinnMottaker(serviceCode = "5441", serviceEdition = "1", virksomhetsnummer = virksomhetsnummer)
    val eksternId = "42"

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(relaxed = true),
            produsentRepository = queryModel
        )
    )

    fun nyBeskjedGql(tekst: String) : String {
        // language=GraphQL
        return """
            mutation {
                nyBeskjed(nyBeskjed: {
                    metadata: {
                        eksternId: "$eksternId"
                        virksomhetsnummer: "${mottaker.virksomhetsnummer}"
                    }
                    notifikasjon: {
                        tekst: "$tekst"
                        merkelapp: "tag"
                        lenke: "#bar"
                    }
                    mottaker: {altinn: {
                        serviceCode: "${mottaker.serviceCode}"
                        serviceEdition: "${mottaker.serviceEdition}"
                    }}
                }) {
                    __typename
                    ... on Error { feilmelding }
                    ... on NyBeskjedVellykket { id }
                }
            }
            """
    }

    fun nyOppgaveGql(tekst: String) : String {
        // language=GraphQL
        return """
            mutation {
                nyOppgave(nyOppgave: {
                    metadata: {
                        eksternId: "$eksternId"
                        virksomhetsnummer: "${mottaker.virksomhetsnummer}"
                    }
                    notifikasjon: {
                        tekst: "$tekst"
                        merkelapp: "tag"
                        lenke: "#bar"
                    }
                    mottaker: {altinn: {
                        serviceCode: "${mottaker.serviceCode}"
                        serviceEdition: "${mottaker.serviceEdition}"
                    }}
                }) {
                    __typename
                    ... on Error { feilmelding }
                    ... on NyOppgaveVellykket { id }
                }
            }
            """
    }

    describe("Idempotens Oppførsel for Produsent api") {
        context("Beskjed med samme tekst") {
            val idNyBeskjed1 = engine.produsentApi(nyBeskjedGql("foo")).getTypedContent<UUID>("/nyBeskjed/id")
            val idNyBeskjed2 = engine.produsentApi(nyBeskjedGql("foo")).getTypedContent<UUID>("/nyBeskjed/id")

            it("er opprettet med samme id") {
                idNyBeskjed1 shouldBe idNyBeskjed2
            }
        }

        context("Beskjed med ulik tekst") {
            val resultat1 = engine.produsentApi(nyBeskjedGql("foo")).getTypedContent<String>("/nyBeskjed/__typename")
            val resultat2 = engine.produsentApi(nyBeskjedGql("bar")).getTypedContent<String>("/nyBeskjed/__typename")

            it("første kall er opprettet") {
                resultat1 shouldBe MutationNyBeskjed.NyBeskjedVellykket::class.simpleName
            }
            it("andre kall er feilmelding") {
                resultat2 shouldBe Error.DuplikatEksternIdOgMerkelapp::class.simpleName
            }
        }

        context("Oppgave med samme tekst") {
            val idNyOppgave1 = engine.produsentApi(nyOppgaveGql("foo")).getTypedContent<UUID>("/nyOppgave/id")
            val idNyOppgave2 = engine.produsentApi(nyOppgaveGql("foo")).getTypedContent<UUID>("/nyOppgave/id")

            it("er opprettet med samme id") {
                idNyOppgave1 shouldBe idNyOppgave2
            }
        }

        context("Oppgave med ulik tekst") {
            val resultat1 = engine.produsentApi(nyOppgaveGql("foo")).getTypedContent<String>("/nyOppgave/__typename")
            val resultat2 = engine.produsentApi(nyOppgaveGql("bar")).getTypedContent<String>("/nyOppgave/__typename")

            it("første kall er opprettet") {
                resultat1 shouldBe MutationNyOppgave.NyOppgaveVellykket::class.simpleName
            }
            it("andre kall er feilmelding") {
                resultat2 shouldBe Error.DuplikatEksternIdOgMerkelapp::class.simpleName
            }
        }
    }
})
