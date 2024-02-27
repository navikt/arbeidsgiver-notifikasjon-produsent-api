package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.NyNotifikasjonInputType.*
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import org.intellij.lang.annotations.Language
import java.util.*

@Suppress("EnumEntryName")
enum class NyNotifikasjonInputType(val returType: String) {
    nyBeskjed("NyBeskjedVellykket"),
    nyOppgave("NyOppgaveVellykket"),
    nyKalenderavtale("NyKalenderavtaleVellykket"),
}


/* Her tester vi også at graphql-biblioteket vårt støtter å sende inn json-strukturerer som input. */
@Language("json")
private val jsonVariabler = laxObjectMapper.readValue<Map<String, Any?>>("""
{
  "eksterneVarsler": [
    {
      "sms": {
        "mottaker": {
          "kontaktinfo": {
            "fnr": "",
            "tlf": ""
          }
        },
        "smsTekst": "En test SMS",
        "sendetidspunkt": {
          "sendevindu": "NKS_AAPNINGSTID"
        }
      }
    },
    {
      "epost": {
        "mottaker": {
          "kontaktinfo": {
            "fnr": "0",
            "epostadresse": "0"
          }
        },
        "epostTittel": "En tittel til din epost",
        "epostHtmlBody": "<body><h1>hei</h1></body>",
        "sendetidspunkt": {
          "sendevindu": "NKS_AAPNINGSTID"
        }
      }
    },
    {
      "altinntjeneste": {
        "mottaker": {
          "serviceCode": "1337",
          "serviceEdition": "42"
        },
        "tittel": "Følg med, du har nye følgere å følge opp",
        "innhold": "Gå inn på Nav sine nettsider og følg veiledningen",
        "sendetidspunkt": {
          "sendevindu": "NKS_AAPNINGSTID"
        }
      }
    }
  ]
}
""")

class EksternVarselApiTests: DescribeSpec({

    fun nyNotifikasjonMutation(type: NyNotifikasjonInputType) = when(type) {
        nyBeskjed,
        nyOppgave -> """
            mutation LagNotifikasjon(${'$'}eksterneVarsler: [EksterntVarselInput!]!) {
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
                        eksterneVarsler: ${'$'}eksterneVarsler
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
        nyKalenderavtale -> """
            mutation LagNotifikasjon(${'$'}eksterneVarsler: [EksterntVarselInput!]!) {
                nyNotifikasjon: nyKalenderavtale(
                    mottakere: {
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }
                    lenke: "0"
                    tekst: "0"
                    merkelapp: "tag"
                    grupperingsid: "0"
                    virksomhetsnummer: "0"
                    eksternId: "$type-0"
                    startTidspunkt: "2021-01-01T00:00:00"
                    eksterneVarsler: ${'$'}eksterneVarsler
                ) {
                    __typename
                    ... on NyKalenderavtaleVellykket{
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
    }


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
                                ... on Kalenderavtale {
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
        val (produsentModel, engine) = setupEngine()
        val nyNotifikasjonResult = engine.produsentApi(
            GraphQLRequest(
                query = nyNotifikasjonMutation(nyBeskjed),
                variables = jsonVariabler,
            )
        )
        val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
        val id0 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/0/id")
        val id1 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/1/id")
        val id2 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/2/id")

        // sjekk varsel-status er 'bestillt' via graphql
        val mineNotifikasjonerResult = engine.produsentApi(mineNotifikasjonerQuery)
        val varsel0 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/0")
        val varsel1 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/1")
        val varsel2 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/2")

        it("bestilling registrert") {
            varsel0.id shouldBeIn listOf(id0, id1, id2)
            varsel1.id shouldBeIn listOf(id0, id1, id2)
            varsel2.id shouldBeIn listOf(id0, id1, id2)
            setOf(id0, id1, id2) shouldHaveSize 3

            varsel0.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
            varsel1.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
            varsel2.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
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

        produsentModel.oppdaterModellEtterHendelse(
            EksterntVarselFeilet(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = UUID.randomUUID(),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id2,
                råRespons = NullNode.instance,
                feilmelding = "En feil har skjedd",
                altinnFeilkode = "12345",
            )
        )

        val mineNotifikasjonerResult2 = engine.produsentApi(mineNotifikasjonerQuery)
        val oppdaterteVarsler = listOf<QueryNotifikasjoner.EksterntVarsel>(
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/0"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/1"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/2"),
        )
        val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 } !!
        val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 } !!
        val oppdatertVarsel2 = oppdaterteVarsler.find { it.id == id2 } !!

        it("status-oppdatering reflektert i graphql-endepunkt") {
            oppdatertVarsel0.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.SENDT
            oppdatertVarsel1.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.FEILET
            oppdatertVarsel2.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.FEILET
        }
    }

    describe("Oppretter oppgave med eksterne varsler som sendes OK") {
        val (produsentModel, engine) = setupEngine()
        val nyNotifikasjonResult = engine.produsentApi(GraphQLRequest(
            query = nyNotifikasjonMutation(nyOppgave),
            variables = jsonVariabler,
        ))
        val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
        val id0 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/0/id")
        val id1 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/1/id")
        val id2 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/2/id")

        // sjekk varsel-status er 'bestillt' via graphql
        val mineNotifikasjonerResult = engine.produsentApi(mineNotifikasjonerQuery)
        val varsel0 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/0")
        val varsel1 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/1")
        val varsel2 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/2")

        it("bestilling registrert") {
            varsel0.id shouldBeIn listOf(id0, id1, id2)
            varsel1.id shouldBeIn listOf(id0, id1, id2)
            varsel2.id shouldBeIn listOf(id0, id1, id2)
            setOf(id0, id1, id2) shouldHaveSize 3

            varsel0.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
            varsel1.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
            varsel2.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
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

        produsentModel.oppdaterModellEtterHendelse(
            EksterntVarselFeilet(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = UUID.randomUUID(),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id2,
                råRespons = NullNode.instance,
                feilmelding = "En feil har skjedd",
                altinnFeilkode = "12345",
            )
        )

        val mineNotifikasjonerResult2 = engine.produsentApi(mineNotifikasjonerQuery)
        val oppdaterteVarsler = listOf<QueryNotifikasjoner.EksterntVarsel>(
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/0"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/1"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/2"),
        )
        val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 } !!
        val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 } !!
        val oppdatertVarsel2 = oppdaterteVarsler.find { it.id == id2 } !!

        it("status-oppdatering reflektert i graphql-endepunkt") {
            oppdatertVarsel0.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.SENDT
            oppdatertVarsel1.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.FEILET
            oppdatertVarsel2.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.FEILET
        }
    }

    describe("Oppretter kalenderavtale med eksterne varsler som sendes OK") {
        val (produsentModel, engine) = setupEngine()
        produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.SakOpprettet.copy(
            virksomhetsnummer = "0",
            merkelapp = "tag",
            grupperingsid = "0"
        ))
        val nyNotifikasjonResult = engine.produsentApi(GraphQLRequest(
            query = nyNotifikasjonMutation(nyKalenderavtale),
            variables = jsonVariabler,
        ))
        val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
        val id0 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/0/id")
        val id1 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/1/id")
        val id2 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/2/id")

        // sjekk varsel-status er 'bestillt' via graphql
        val mineNotifikasjonerResult = engine.produsentApi(mineNotifikasjonerQuery)
        val varsel0 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/0")
        val varsel1 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/1")
        val varsel2 = mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/2")

        it("bestilling registrert") {
            varsel0.id shouldBeIn listOf(id0, id1, id2)
            varsel1.id shouldBeIn listOf(id0, id1, id2)
            varsel2.id shouldBeIn listOf(id0, id1, id2)
            setOf(id0, id1, id2) shouldHaveSize 3

            varsel0.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
            varsel1.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
            varsel2.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.NY
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

        produsentModel.oppdaterModellEtterHendelse(
            EksterntVarselFeilet(
                virksomhetsnummer = "0",
                notifikasjonId = notId,
                hendelseId = UUID.randomUUID(),
                produsentId = "0",
                kildeAppNavn = "0",
                varselId = id2,
                råRespons = NullNode.instance,
                feilmelding = "En feil har skjedd",
                altinnFeilkode = "12345",
            )
        )

        val mineNotifikasjonerResult2 = engine.produsentApi(mineNotifikasjonerQuery)
        val oppdaterteVarsler = listOf<QueryNotifikasjoner.EksterntVarsel>(
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/0"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/1"),
            mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/2"),
        )
        val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 } !!
        val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 } !!
        val oppdatertVarsel2 = oppdaterteVarsler.find { it.id == id2 } !!

        it("status-oppdatering reflektert i graphql-endepunkt") {
            oppdatertVarsel0.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.SENDT
            oppdatertVarsel1.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.FEILET
            oppdatertVarsel2.status shouldBe QueryNotifikasjoner.EksterntVarselStatus.FEILET
        }
    }

})

private fun DescribeSpec.setupEngine(): Pair<ProdusentRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val engine = ktorProdusentTestServer(
        produsentRepository = produsentModel
    )
    return Pair(produsentModel, engine)
}