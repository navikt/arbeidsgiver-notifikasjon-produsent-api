package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldNot
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.fakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class NyOppgavePaaminnelseTests : DescribeSpec({
    val stubbedKafkaProducer = fakeHendelseProdusent()
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val engine = ktorProdusentTestServer(
        kafkaProducer = stubbedKafkaProducer,
        produsentRepository = produsentRepository,
    )

    describe("oppgave med frist konkret tidspunkt for påminnelse") {
        val response = engine.produsentApi(
            nyOppgave(
                "2019-11-01T00:00:00Z",
                """
                    frist: "2020-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2019-12-01T00:00:00"
                        }
                    }
                """,
            )
        )
        it("opprettes uten feil") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            (stubbedKafkaProducer.hendelser.first() as HendelseModel.OppgaveOpprettet).påminnelse shouldNot beNull()
        }
    }

    describe("oppgave uten frist konkret tidspunkt for påminnelse") {
        val response = engine.produsentApi(
            nyOppgave(
                "2019-11-01T00:00:00Z",
                """
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2020-01-02T00:00:00"
                        }
                    }
                """,
            )
        )
        it("opprettes uten feil") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            (stubbedKafkaProducer.hendelser.first() as HendelseModel.OppgaveOpprettet).påminnelse shouldNot beNull()
        }
    }
    describe("oppgave med frist konkret tidspunkt for påminnelse etter frist") {
        val response = engine.produsentApi(
            nyOppgave(
                "2019-11-01T00:00:00Z",
                """
                    frist: "2020-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2020-01-02T00:00:00"
                        }
                    }
                """,
            )
        )
        it("opprettelse feiler med feilmelding") {
            response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
        }
    }
    describe("oppgave uten frist konkret tidspunkt for påminnelse før opprettelse") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-02T00:00:00Z",
                """
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2020-01-01T00:00:00"
                        }
                    }
                """,
            )
        )
        it("opprettelse feiler med feilmelding") {
            response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
        }
    }



    describe("oppgave uten frist, tidspunkt for påminnelse relativ til opprettelse") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    paaminnelse: {
                        tidspunkt: {
                            etterOpprettelse: "P2DT3H4M"
                        }
                    }
                """,
            )
        )
        it("opprettes uten feil") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            (stubbedKafkaProducer.hendelser.first() as HendelseModel.OppgaveOpprettet).påminnelse shouldNot beNull()
        }
    }

    describe("oppgave uten frist, tidspunkt for påminnelse relativ til frist") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                    }
                """,
            )
        )
        it("opprettelse feiler med feilmelding") {
            response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
        }
    }

    describe("oppgave med frist, tidspunkt for påminnelse relativ til frist") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                    }
                """,
            )
        )
        it("opprettes uten feil") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            (stubbedKafkaProducer.hendelser.first() as HendelseModel.OppgaveOpprettet).påminnelse shouldNot beNull()
        }
    }

    describe("oppgave med frist, tidspunkt for påminnelse relativ til frist blir før opprettelse") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    frist: "2020-01-02"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                    }
                """,
            )
        )
        it("opprettelse feiler med feilmelding") {
            response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
        }
    }

    // TODO: med eksternevarsler
})

private fun nyOppgave(opprettetTidspunkt: String, fragment: String) = """
            mutation {
                nyOppgave(nyOppgave: {
                    $fragment
                    mottaker: {
                        naermesteLeder: {
                            naermesteLederFnr: "12345678910",
                            ansattFnr: "321"
                        } 
                    }
                    metadata: {
                        eksternId: "0"
                        virksomhetsnummer: "0"
                        opprettetTidspunkt: "$opprettetTidspunkt"
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
                        eksterneVarsler {
                            id
                        }
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            
            }
        """