package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
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
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.intellij.lang.annotations.Language
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("EnumEntryName")
enum class NyNotifikasjonInputType(val returType: String) {
    nyBeskjed("NyBeskjedVellykket"),
    nyOppgave("NyOppgaveVellykket"),
    nyKalenderavtale("NyKalenderavtaleVellykket"),
}


/* Her tester vi også at graphql-biblioteket vårt støtter å sende inn json-strukturerer som input. */
@Language("json")
private val jsonVariabler = laxObjectMapper.readValue<Map<String, Any?>>(
    """
{
  "eksterneVarsler": [
    {
      "sms": {
        "mottaker": {
          "kontaktinfo": {
            "fnr": "",
            "tlf": "90000000"
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
            "epostadresse": "foo@foo.com"
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
    },
    {
      "altinnressurs": {
        "mottaker": {
          "ressursId": "nav_foo_bar"
        },
        "epostTittel": "En tittel til din epost",
        "epostHtmlBody": "<body><h1>hei</h1></body>",
        "smsTekst": "En test SMS",
        "sendetidspunkt": {
          "sendevindu": "NKS_AAPNINGSTID"
        }
      }
    }
  ]
}
"""
)

class EksternVarselApiTest {

    fun nyNotifikasjonMutation(type: NyNotifikasjonInputType) = when (type) {
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
                                serviceCode: "1"
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
                            serviceCode: "1"
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

    @Test
    fun `Oppretter beskjed med eksterne varsler som sendes OK`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = produsentModel
        ) {
            val nyNotifikasjonResult = client.produsentApi(
                GraphQLRequest(
                    query = nyNotifikasjonMutation(nyBeskjed),
                    variables = jsonVariabler,
                )
            )
            val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
            val id0 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/0/id")
            val id1 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/1/id")
            val id2 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/2/id")
            val id3 = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/eksterneVarsler/3/id")

            // sjekk varsel-status er 'bestillt' via graphql
            val mineNotifikasjonerResult = client.produsentApi(mineNotifikasjonerQuery)
            val varsel0 =
                mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/0")
            val varsel1 =
                mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/1")
            val varsel2 =
                mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/2")
            val varsel3 =
                mineNotifikasjonerResult.getTypedContent<QueryNotifikasjoner.EksterntVarsel>("mineNotifikasjoner/edges/0/node/eksterneVarsler/3")

            // bestilling registrert
            assertEquals(listOf(id0, id1, id2, id3), listOf(varsel0.id, varsel1.id, varsel2.id, varsel3.id))
            assertEquals(4, setOf(id0, id1, id2, id3).size)

            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel0.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel1.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel2.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel3.status)


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

            produsentModel.oppdaterModellEtterHendelse(
                EksterntVarselFeilet(
                    virksomhetsnummer = "0",
                    notifikasjonId = notId,
                    hendelseId = UUID.randomUUID(),
                    produsentId = "0",
                    kildeAppNavn = "0",
                    varselId = id3,
                    råRespons = NullNode.instance,
                    feilmelding = "En feil har skjedd",
                    altinnFeilkode = "12345",
                )
            )

            val mineNotifikasjonerResult2 = client.produsentApi(mineNotifikasjonerQuery)
            val oppdaterteVarsler = listOf<QueryNotifikasjoner.EksterntVarsel>(
                mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/0"),
                mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/1"),
                mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/2"),
                mineNotifikasjonerResult2.getTypedContent("mineNotifikasjoner/edges/0/node/eksterneVarsler/3"),
            )
            val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 }!!
            val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 }!!
            val oppdatertVarsel2 = oppdaterteVarsler.find { it.id == id2 }!!
            val oppdatertVarsel3 = oppdaterteVarsler.find { it.id == id3 }!!

            // status-oppdatering reflektert i graphql-endepunkt
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.SENDT, oppdatertVarsel0.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel1.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel2.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel3.status)
        }
    }

    @Test
    fun `Oppretter oppgave med eksterne varsler som sendes OK`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = produsentModel
        ) {
            val nyNotifikasjonResult = client.produsentApi(
                GraphQLRequest(
                    query = nyNotifikasjonMutation(nyOppgave),
                    variables = jsonVariabler,
                )
            )
            val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
            val (id0, id1, id2, id3) = nyNotifikasjonResult.getTypedContent<List<UUID>>("$.nyNotifikasjon.eksterneVarsler.*.id")

            // sjekk varsel-status er 'bestillt' via graphql
            val mineNotifikasjonerResult = client.produsentApi(mineNotifikasjonerQuery)
            val (varsel0, varsel1, varsel2, varsel3) = mineNotifikasjonerResult.getTypedContent<List<QueryNotifikasjoner.EksterntVarsel>>(
                "$.mineNotifikasjoner.edges[0].node.eksterneVarsler.*"
            )

            // bestilling registrert
            assertEquals(listOf(id0, id1, id2, id3), listOf(varsel0.id, varsel1.id, varsel2.id, varsel3.id))
            assertEquals(4, setOf(id0, id1, id2, id3).size)

            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel0.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel1.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel2.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel3.status)


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

            produsentModel.oppdaterModellEtterHendelse(
                EksterntVarselFeilet(
                    virksomhetsnummer = "0",
                    notifikasjonId = notId,
                    hendelseId = UUID.randomUUID(),
                    produsentId = "0",
                    kildeAppNavn = "0",
                    varselId = id3,
                    råRespons = NullNode.instance,
                    feilmelding = "En feil har skjedd",
                    altinnFeilkode = "12345",
                )
            )

            val mineNotifikasjonerResult2 = client.produsentApi(mineNotifikasjonerQuery)
            val oppdaterteVarsler = mineNotifikasjonerResult2.getTypedContent<List<QueryNotifikasjoner.EksterntVarsel>>(
                "$.mineNotifikasjoner.edges[0].node.eksterneVarsler.*"
            )
            val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 }!!
            val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 }!!
            val oppdatertVarsel2 = oppdaterteVarsler.find { it.id == id2 }!!
            val oppdatertVarsel3 = oppdaterteVarsler.find { it.id == id3 }!!

            // status-oppdatering reflektert i graphql-endepunkt
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.SENDT, oppdatertVarsel0.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel1.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel2.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel3.status)
        }
    }

    @Test
    fun `Oppretter kalenderavtale med eksterne varsler som sendes OK`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    virksomhetsnummer = "0",
                    merkelapp = "tag",
                    grupperingsid = "0",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "0",
                            serviceCode = "1",
                            serviceEdition = "1"
                        ),
                    )
                )
            )
            val nyNotifikasjonResult = client.produsentApi(
                GraphQLRequest(
                    query = nyNotifikasjonMutation(nyKalenderavtale),
                    variables = jsonVariabler,
                )
            )
            val notId = nyNotifikasjonResult.getTypedContent<UUID>("nyNotifikasjon/id")
            val (id0, id1, id2, id3) = nyNotifikasjonResult.getTypedContent<List<UUID>>("$.nyNotifikasjon.eksterneVarsler.*.id")

            // sjekk varsel-status er 'bestillt' via graphql
            val mineNotifikasjonerResult = client.produsentApi(mineNotifikasjonerQuery)
            val (varsel0, varsel1, varsel2, varsel3) = mineNotifikasjonerResult.getTypedContent<List<QueryNotifikasjoner.EksterntVarsel>>(
                "$.mineNotifikasjoner.edges[0].node.eksterneVarsler.*"
            )

            // bestilling registrert
            assertEquals(listOf(id0, id1, id2, id3), listOf(varsel0.id, varsel1.id, varsel2.id, varsel3.id))
            assertEquals(4, setOf(id0, id1, id2, id3).size)

            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel0.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel1.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel2.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.NY, varsel3.status)


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

            produsentModel.oppdaterModellEtterHendelse(
                EksterntVarselFeilet(
                    virksomhetsnummer = "0",
                    notifikasjonId = notId,
                    hendelseId = UUID.randomUUID(),
                    produsentId = "0",
                    kildeAppNavn = "0",
                    varselId = id3,
                    råRespons = NullNode.instance,
                    feilmelding = "En feil har skjedd",
                    altinnFeilkode = "12345",
                )
            )

            val mineNotifikasjonerResult2 = client.produsentApi(mineNotifikasjonerQuery)
            val oppdaterteVarsler = mineNotifikasjonerResult2.getTypedContent<List<QueryNotifikasjoner.EksterntVarsel>>(
                "$.mineNotifikasjoner.edges[0].node.eksterneVarsler.*"
            )
            val oppdatertVarsel0 = oppdaterteVarsler.find { it.id == id0 }!!
            val oppdatertVarsel1 = oppdaterteVarsler.find { it.id == id1 }!!
            val oppdatertVarsel2 = oppdaterteVarsler.find { it.id == id2 }!!
            val oppdatertVarsel3 = oppdaterteVarsler.find { it.id == id3 }!!

            // status-oppdatering reflektert i graphql-endepunkt
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.SENDT, oppdatertVarsel0.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel1.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel2.status)
            assertEquals(QueryNotifikasjoner.EksterntVarselStatus.FEILET, oppdatertVarsel3.status)
        }
    }
}