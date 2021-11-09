package no.nav.arbeidsgiver.notifikasjon.produsent_api

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.QueryMineNotifikasjoner
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.util.*

class EksternVarselApiTests: DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val kafkaProducer = mockk<CoroutineKafkaProducer<KafkaKey, Hendelse>>(relaxed = true)

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        )
    )

    val nyBeskjedMutation =
        """
            mutation {
                nyBeskjed(
                    nyBeskjed: {
                        metadata: {
                            eksternId: "0"
                        }
                        mottaker: {
                            altinn: {
                                serviceCode: "5441"
                                serviceEdition: "1"
                                virksomhetsnummer: "0"
                            }
                        }
                        notifikasjon: {
                            merkelapp: "tag"
                            tekst: "0"
                            lenke: "0"
                        } 
                        eksternVarsel: [
                            {sms: {
                                mottaker: {
                                    kontaktinfo: {
                                        fnr: ""
                                        tlf: ""
                                    }
                                }
                                smsTekst: "En test SMS"
                            }}
                            {epost: {
                                mottaker: {
                                    kontaktinfo: {
                                        fnr: "0"
                                        epostadresse: "0"
                                    }
                                }
                                epostTittel: "En tittel til din epost"
                                epostHtmlBody: "<body><h1>hei</h1></body>"
                            }}
                        ]
                    }
                ) {
                    __typename
                    ... on NyBeskjedVellykket {
                        id
                        eksternVarsel {
                            id
                        }
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            }
        """

    val mineNotifikasjonerQuery =
        """
            query {
                mineNotifikasjoner {
                    ... on NotifikasjonConnection {
                        edges {
                            node {
                                ... on Beskjed {
                                    eksternVarsel {
                                        id
                                        status
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

    describe("Oppretter beskjed med eksterne varsler som sendes OK") {
        val nyBeskjedResult = engine.produsentApi(nyBeskjedMutation)
        val notId = nyBeskjedResult.getTypedContent<UUID>("nyBeskjed/id")
        val id0 = nyBeskjedResult.getTypedContent<UUID>("nyBeskjed/eksternVarsel/0/id")
        val id1 = nyBeskjedResult.getTypedContent<UUID>("nyBeskjed/eksternVarsel/1/id")

        // sjekk varsel-status er 'bestillt' via graphql
        val mineNotifikasjonerResult = engine.produsentApi(mineNotifikasjonerQuery)
        val varsel0 = mineNotifikasjonerResult.getTypedContent<QueryMineNotifikasjoner.EksternVarsel>("mineNotifikasjoner/edges/0/node/eksternVarsel")
        val varsel1 = mineNotifikasjonerResult.getTypedContent<QueryMineNotifikasjoner.EksternVarsel>("mineNotifikasjoner/edges/1/node/eksternVarsel")

        it("bestilling registrert") {
            varsel0.id shouldBeIn listOf(id0, id1)
            varsel1.id shouldBeIn listOf(id0, id1)
            varsel0.id shouldNotBe varsel1.id

            varsel0.status shouldBe QueryMineNotifikasjoner.EksternVarselStatus.NY
            varsel1.status shouldBe QueryMineNotifikasjoner.EksternVarselStatus.NY
        }


        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.EksterntVarselVellykket(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = uuid("0"),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id0,
                råRespons = NullNode.instance,
            )
        )

        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.EksterntVarselFeilet(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = uuid("1"),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id1,
                råRespons = NullNode.instance,
                feilmelding = "En feil har skjedd",
                altinnFeilkode = "12345",
            )
        )

        val mineNotifikasjonerResult2 = engine.produsentApi(mineNotifikasjonerQuery)
        val oppdaterteVarsler = listOf<QueryMineNotifikasjoner.EksternVarsel>(
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksternVarsel"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/1/node/eksternVarsel"),
        )
        val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 } !!
        val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 } !!

        it("status-oppdatering reflektert i graphql-endepunkt") {
            oppdatertVarsel0.status shouldBe QueryMineNotifikasjoner.EksternVarselStatus.UTFOERT
            oppdatertVarsel1.status shouldBe QueryMineNotifikasjoner.EksternVarselStatus.FEIL
        }
    }
})