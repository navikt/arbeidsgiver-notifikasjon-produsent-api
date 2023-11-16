package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class IdempotensOppførselForProdusentApiTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val queryModel = ProdusentRepositoryImpl(database)

    val virksomhetsnummer = "1234"
    val mottaker = AltinnMottaker(serviceCode = "5441", serviceEdition = "1", virksomhetsnummer = virksomhetsnummer)
    val eksternId = "42"

    val engine = ktorProdusentTestServer(
        produsentRepository = queryModel
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
                    eksterneVarsler: [
                        { sms: {
                            mottaker: {
                                kontaktinfo: {
                                    fnr: ""
                                    tlf: ""
                                }
                            },
                            smsTekst: "En test SMS",
                            sendetidspunkt: {
                                sendevindu: NKS_AAPNINGSTID
                            }
                        }},
                        { epost: {
                            mottaker: {
                                kontaktinfo: {
                                    fnr: "0",
                                    epostadresse: "0"
                                }
                            }
                            epostTittel: "En tittel til din epost"
                            epostHtmlBody: "<body><h1>hei</h1></body>"
                            sendetidspunkt: {
                                sendevindu: LOEPENDE
                            }
                        }}
                    ]
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
                    eksterneVarsler: [
                        { sms: {
                            mottaker: {
                                kontaktinfo: {
                                    fnr: ""
                                    tlf: ""
                                }
                            },
                            smsTekst: "En test SMS",
                            sendetidspunkt: {
                                sendevindu: NKS_AAPNINGSTID
                            }
                        }},
                        { epost: {
                            mottaker: {
                                kontaktinfo: {
                                    fnr: "0",
                                    epostadresse: "0"
                                }
                            }
                            epostTittel: "En tittel til din epost"
                            epostHtmlBody: "<body><h1>hei</h1></body>"
                            sendetidspunkt: {
                                sendevindu: LOEPENDE
                            }
                        }}
                    ]
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

        context("ny oppgave er idempotent også når varsler er sendt") {
            val resultat1 = engine.produsentApi(nyOppgaveGql("foo"))

            it("første kall er opprettet") {
                resultat1.getTypedContent<String>("/nyOppgave/__typename") shouldBe MutationNyOppgave.NyOppgaveVellykket::class.simpleName
            }

            val idNyOppgave1 = resultat1.getTypedContent<UUID>("/nyOppgave/id")

            it ("og varsler blir markert som sendt") {
                val notifikasjon = queryModel.hentNotifikasjon(idNyOppgave1)!!
                notifikasjon.eksterneVarsler.forEach {
                    queryModel.oppdaterModellEtterHendelse(HendelseModel.EksterntVarselVellykket(
                        notifikasjonId = idNyOppgave1,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        hendelseId = UUID.randomUUID(),
                        produsentId = "fager",
                        kildeAppNavn = "test",
                        varselId = it.varselId,
                        råRespons = NullNode.instance,
                    ))
                }
            }

            it("andre kall er idempotent") {
                val idNyOppgave2 = engine.produsentApi(nyOppgaveGql("foo")).getTypedContent<UUID>("/nyOppgave/id")
                idNyOppgave2 shouldBe idNyOppgave1
            }
        }
    }
})
