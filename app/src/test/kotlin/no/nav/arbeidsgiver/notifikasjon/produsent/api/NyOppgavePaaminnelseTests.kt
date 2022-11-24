package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
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

    describe("ekstern varlser med tom liste") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: []
                    }
                """,
            )
        )
        it("opprettelse uten varsler") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
            hendelse.påminnelse!!.eksterneVarsler shouldBe emptyList()
        }
    }

    describe("ekstern varlser med 1 sms") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                sms: {
                                    mottaker: {
                                        kontaktinfo: {
                                            tlf: "1234"
                                        }
                                    }
                                    smsTekst: "hei"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
            )
        )
        it("opprettelse ok, med en sms") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
            hendelse.påminnelse?.eksterneVarsler?.size shouldBe 1
            val varsel = hendelse.påminnelse!!.eksterneVarsler[0]
            varsel shouldBe HendelseModel.SmsVarselKontaktinfo(
                varselId = varsel.varselId,
                tlfnr = "1234",
                fnrEllerOrgnr = "0",
                smsTekst = "hei",
                sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                sendeTidspunkt = null,
            )
        }
    }

    describe("ekstern varlser med 1 epost") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                epost: {
                                    mottaker: {
                                        kontaktinfo: {
                                            epostadresse: "1234@1234.no"
                                        }
                                    }
                                    epostTittel: "hei"
                                    epostHtmlBody: "body"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
            )
        )
        it("opprettelse ok, med en epost") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
            hendelse.påminnelse?.eksterneVarsler?.size shouldBe 1
            val varsel = hendelse.påminnelse!!.eksterneVarsler[0]
            varsel shouldBe HendelseModel.EpostVarselKontaktinfo(
                varselId = varsel.varselId,
                epostAddr = "1234@1234.no",
                fnrEllerOrgnr = "0",
                tittel = "hei",
                htmlBody = "body",
                sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                sendeTidspunkt = null,
            )
        }
    }

    describe("ekstern varlser med epost og sms") {
        val response = engine.produsentApi(
            nyOppgave(
                "2020-01-01T00:00:00Z",
                """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                epost: {
                                    mottaker: {
                                        kontaktinfo: {
                                            epostadresse: "1234@1234.no"
                                        }
                                    }
                                    epostTittel: "hei"
                                    epostHtmlBody: "body"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                            {
                                sms: {
                                    mottaker: {
                                        kontaktinfo: {
                                            tlf: "1234"
                                        }
                                    }
                                    smsTekst: "hei"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
            )
        )
        it("opprettelse ok, med sms og epost") {
            response.getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            // TODO: sjekke at ID-er for eksterne varsler blir generert og returnert?
            val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
            hendelse.påminnelse?.eksterneVarsler?.size shouldBe 2
        }
    }
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