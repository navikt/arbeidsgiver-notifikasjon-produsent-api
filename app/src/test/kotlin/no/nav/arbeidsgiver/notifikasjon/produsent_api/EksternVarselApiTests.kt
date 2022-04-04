package no.nav.arbeidsgiver.notifikasjon.produsent_api

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.QueryMineNotifikasjoner
import no.nav.arbeidsgiver.notifikasjon.produsent_api.NyNotifikasjonInputType.nyBeskjed
import no.nav.arbeidsgiver.notifikasjon.produsent_api.NyNotifikasjonInputType.nyOppgave
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

@Suppress("EnumEntryName")
enum class NyNotifikasjonInputType(val returType: String) {
    nyBeskjed("NyBeskjedVellykket"),
    nyOppgave("NyOppgaveVellykket")
}

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

    fun nyNotifikasjonMutation(type: NyNotifikasjonInputType) =
        """
            mutation {
                nyNotifikasjon: $type(
                    $type: {
                        metadata: {
                            eksternId: "$type-0"
                            virksomhetsnummer: "0"
                        }
                        mottaker: {
                            altinn: {
                                serviceCode: "5441"
                                serviceEdition: "1"
                            }
                        }
                        notifikasjon: {
                            merkelapp: "tag"
                            tekst: "0"
                            lenke: "0"
                        } 
                        eksterneVarsler: [
                            {sms: {
                                mottaker: {
                                    kontaktinfo: {
                                        fnr: ""
                                        tlf: ""
                                    }
                                }
                                smsTekst: "En test SMS"
                                sendetidspunkt: {
                                    sendevindu: NKS_AAPNINGSTID
                                }
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
                                sendetidspunkt: {
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }}
                        ]
                    }
                ) {
                    __typename
                    ... on ${type.returType} {
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

    val mineNotifikasjonerQuery =
        """
            query {
                mineNotifikasjoner {
                    ... on NotifikasjonConnection {
                        edges {
                            node {
                                ... on Beskjed {
                                    eksterneVarsler {
                                        id
                                        status
                                    }
                                }
                                ... on Oppgave {
                                    eksterneVarsler {
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
        val nyNotifikasjonResult = engine.produsentApi(nyNotifikasjonMutation(nyBeskjed))
        val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
        val id0 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/0/id")
        val id1 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/1/id")

        // sjekk varsel-status er 'bestillt' via graphql
        val mineNotifikasjonerResult = engine.produsentApi(mineNotifikasjonerQuery)
        val varsel0 = mineNotifikasjonerResult.getTypedContent<QueryMineNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/0")
        val varsel1 = mineNotifikasjonerResult.getTypedContent<QueryMineNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/1")

        it("bestilling registrert") {
            varsel0.id shouldBeIn listOf(id0, id1)
            varsel1.id shouldBeIn listOf(id0, id1)
            varsel0.id shouldNotBe varsel1.id

            varsel0.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.NY
            varsel1.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.NY
        }


        produsentModel.oppdaterModellEtterHendelse(
            EksterntVarselVellykket(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = UUID.randomUUID(),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id0,
                råRespons = NullNode.instance,
            )
        )

        produsentModel.oppdaterModellEtterHendelse(
            EksterntVarselFeilet(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = UUID.randomUUID(),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id1,
                råRespons = NullNode.instance,
                feilmelding = "En feil har skjedd",
                altinnFeilkode = "12345",
            )
        )

        val mineNotifikasjonerResult2 = engine.produsentApi(mineNotifikasjonerQuery)
        val oppdaterteVarsler = listOf<QueryMineNotifikasjoner.EksterntVarsel>(
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/0"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/1"),
        )
        val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 } !!
        val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 } !!

        it("status-oppdatering reflektert i graphql-endepunkt") {
            oppdatertVarsel0.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.SENDT
            oppdatertVarsel1.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.FEILET
        }
    }
    describe("Oppretter oppgave med eksterne varsler som sendes OK") {
        val nyNotifikasjonResult = engine.produsentApi(nyNotifikasjonMutation(nyOppgave))
        val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
        val id0 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/0/id")
        val id1 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/1/id")

        // sjekk varsel-status er 'bestillt' via graphql
        val mineNotifikasjonerResult = engine.produsentApi(mineNotifikasjonerQuery)
        val varsel0 = mineNotifikasjonerResult.getTypedContent<QueryMineNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/0")
        val varsel1 = mineNotifikasjonerResult.getTypedContent<QueryMineNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/1")

        it("bestilling registrert") {
            varsel0.id shouldBeIn listOf(id0, id1)
            varsel1.id shouldBeIn listOf(id0, id1)
            varsel0.id shouldNotBe varsel1.id

            varsel0.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.NY
            varsel1.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.NY
        }


        produsentModel.oppdaterModellEtterHendelse(
            EksterntVarselVellykket(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = UUID.randomUUID(),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id0,
                råRespons = NullNode.instance,
            )
        )

        produsentModel.oppdaterModellEtterHendelse(
            EksterntVarselFeilet(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = UUID.randomUUID(),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id1,
                råRespons = NullNode.instance,
                feilmelding = "En feil har skjedd",
                altinnFeilkode = "12345",
            )
        )

        val mineNotifikasjonerResult2 = engine.produsentApi(mineNotifikasjonerQuery)
        val oppdaterteVarsler = listOf<QueryMineNotifikasjoner.EksterntVarsel>(
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/0"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/1"),
        )
        val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 } !!
        val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 } !!

        it("status-oppdatering reflektert i graphql-endepunkt") {
            oppdatertVarsel0.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.SENDT
            oppdatertVarsel1.status shouldBe QueryMineNotifikasjoner.EksterntVarselStatus.FEILET
        }
    }

})