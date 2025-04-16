package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class IdempotensOppførselForProdusentApiTest {

    private val virksomhetsnummer = "1"
    private val mottaker =
        AltinnMottaker(serviceCode = "1", serviceEdition = "1", virksomhetsnummer = virksomhetsnummer)
    private val eksternId = "42"
    private val grupperingsid = "42"

    private fun nyBeskjedGql(tekst: String) =
        // language=GraphQL
        """
            mutation NyBeskjed(${'$'}eksterneVarsler: [EksterntVarselInput!]! = []) {
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
                    eksterneVarsler: ${'$'}eksterneVarsler
                }) {
                    __typename
                    ... on Error { feilmelding }
                    ... on NyBeskjedVellykket { id }
                }
            }
            """

    private fun nyOppgaveGql(tekst: String) =
        // language=GraphQL
        """
            mutation NyOppgave(${'$'}eksterneVarsler: [EksterntVarselInput!]! = []) {
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
                    eksterneVarsler: ${'$'}eksterneVarsler
                }) {
                    __typename
                    ... on Error { feilmelding }
                    ... on NyOppgaveVellykket { id }
                }
            }
            """

    private fun nyKalenderavtaleGql(tekst: String) =
        // language=GraphQL
        """
            mutation NyKalenderavtale(
                ${'$'}startTidspunkt: ISO8601DateTime! = "2024-10-12T07:00:00.00"
                ${'$'}sluttTidspunkt: ISO8601DateTime
                ${'$'}lokasjon: LokasjonInput
                ${'$'}erDigitalt: Boolean
                ${'$'}tilstand: KalenderavtaleTilstand
                ${'$'}eksterneVarsler: [EksterntVarselInput!]! = []
            ) {
                nyKalenderavtale(
                    grupperingsid: "$grupperingsid",
                    eksternId: "$eksternId"
                    virksomhetsnummer: "${mottaker.virksomhetsnummer}"
                    tekst: "$tekst"
                    merkelapp: "tag"
                    lenke: "#bar"
                    mottakere: [{altinn: {
                        serviceCode: "${mottaker.serviceCode}"
                        serviceEdition: "${mottaker.serviceEdition}"
                    }}]
                    startTidspunkt: ${'$'}startTidspunkt
                    sluttTidspunkt: ${'$'}sluttTidspunkt
                    lokasjon: ${'$'}lokasjon
                    erDigitalt: ${'$'}erDigitalt
                    tilstand: ${'$'}tilstand
                    eksterneVarsler: ${'$'}eksterneVarsler
                ) {
                    __typename
                    ... on Error { feilmelding }
                    ... on NyKalenderavtaleVellykket { id }
                }
            }
            """


    @Test
    fun `Beskjed med samme tekst`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val idNyBeskjed1 = client.produsentApi(nyBeskjedGql("foo")).getTypedContent<UUID>("/nyBeskjed/id")
            val idNyBeskjed2 = client.produsentApi(nyBeskjedGql("foo")).getTypedContent<UUID>("/nyBeskjed/id")

            // er opprettet med samme id
            assertEquals(idNyBeskjed2, idNyBeskjed1)
        }
    }

    @Test
    fun `Beskjed med ulik tekst`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val resultat1 = client.produsentApi(nyBeskjedGql("foo")).getTypedContent<String>("/nyBeskjed/__typename")
            val resultat2 = client.produsentApi(nyBeskjedGql("bar")).getTypedContent<String>("/nyBeskjed/__typename")

            // første kall er opprettet
            assertEquals(MutationNyBeskjed.NyBeskjedVellykket::class.simpleName, resultat1)
            // andre kall er feilmelding
            assertEquals(Error.DuplikatEksternIdOgMerkelapp::class.simpleName, resultat2)
        }
    }

    @Test
    fun `Oppgave med samme tekst`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val idNyOppgave1 = client.produsentApi(nyOppgaveGql("foo")).getTypedContent<UUID>("/nyOppgave/id")
            val idNyOppgave2 = client.produsentApi(nyOppgaveGql("foo")).getTypedContent<UUID>("/nyOppgave/id")

            // er opprettet med samme id
            assertEquals(idNyOppgave2, idNyOppgave1)
        }
    }

    @Test
    fun `Oppgave med ulik tekst`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val resultat1 = client.produsentApi(nyOppgaveGql("foo")).getTypedContent<String>("/nyOppgave/__typename")
            val resultat2 = client.produsentApi(nyOppgaveGql("bar")).getTypedContent<String>("/nyOppgave/__typename")

            // første kall er opprettet
            assertEquals(MutationNyOppgave.NyOppgaveVellykket::class.simpleName, resultat1)
            // andre kall er feilmelding
            assertEquals(Error.DuplikatEksternIdOgMerkelapp::class.simpleName, resultat2)
        }
    }

    @Test
    fun `Kalenderavtale med samme tekst`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val queryModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = queryModel
        ) {
            queryModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    merkelapp = "tag",
                    grupperingsid = grupperingsid,
                )
            )
            val idNyKalenderavtale1 =
                client.produsentApi(nyKalenderavtaleGql("foo")).getTypedContent<UUID>("/nyKalenderavtale/id")
            val idNyKalenderavtale2 =
                client.produsentApi(nyKalenderavtaleGql("foo")).getTypedContent<UUID>("/nyKalenderavtale/id")

            // er opprettet med samme id
            assertEquals(idNyKalenderavtale2, idNyKalenderavtale1)
        }
    }

    @Test
    fun `Kalenderavtale med ulik tekst`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val queryModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = queryModel
        ) {
            queryModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    merkelapp = "tag",
                    grupperingsid = grupperingsid,
                )
            )
            val resultat1 =
                client.produsentApi(nyKalenderavtaleGql("foo")).getTypedContent<String>("/nyKalenderavtale/__typename")
            val resultat2 =
                client.produsentApi(nyKalenderavtaleGql("bar")).getTypedContent<String>("/nyKalenderavtale/__typename")

            // første kall er opprettet
            assertEquals(MutationKalenderavtale.NyKalenderavtaleVellykket::class.simpleName, resultat1)
            // andre kall er feilmelding
            assertEquals(Error.DuplikatEksternIdOgMerkelapp::class.simpleName, resultat2)
        }
    }

    @Test
    fun `Beskjed med varsler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val nyBeskjedReq = GraphQLRequest(
                query = nyBeskjedGql("foo"),
                variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser1),
            )
            val idNyBeskjed1 = client.produsentApi(nyBeskjedReq).getTypedContent<UUID>("/nyBeskjed/id")
            val idNyBeskjed2 = client.produsentApi(nyBeskjedReq).getTypedContent<UUID>("/nyBeskjed/id")

            // er opprettet med samme id
            assertEquals(idNyBeskjed2, idNyBeskjed1)
        }
    }

    @Test
    fun `Oppgave med varsler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val nyOppgaveReq = GraphQLRequest(
                query = nyOppgaveGql("foo"),
                variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser1),
            )
            val idNyOppgave1 = client.produsentApi(nyOppgaveReq).getTypedContent<UUID>("/nyOppgave/id")
            val idNyOppgave2 = client.produsentApi(nyOppgaveReq).getTypedContent<UUID>("/nyOppgave/id")

            // er opprettet med samme id
            assertEquals(idNyOppgave2, idNyOppgave1)
        }
    }

    @Test
    fun `Kalenderavtale med varsler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val queryModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = queryModel
        ) {
            queryModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    merkelapp = "tag",
                    grupperingsid = grupperingsid,
                )
            )
            val graphQLRequest = GraphQLRequest(
                query = nyKalenderavtaleGql("foo"),
                variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser1),
            )
            val idNyKalenderavtale = client.produsentApi(graphQLRequest).getTypedContent<UUID>("/nyKalenderavtale/id")
            val idNyKalenderavtale2 = client.produsentApi(graphQLRequest).getTypedContent<UUID>("/nyKalenderavtale/id")

            // er opprettet med samme id
            assertEquals(idNyKalenderavtale2, idNyKalenderavtale)
        }
    }

    @Test
    fun `ny oppgave er idempotent også når varsler er sendt`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val queryModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = queryModel
        ) {
            val nyOppgaveReq = GraphQLRequest(
                query = nyOppgaveGql("foo"),
                variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser1),
            )
            val resultat1 = client.produsentApi(nyOppgaveReq)

            // første kall er opprettet
            assertEquals(
                MutationNyOppgave.NyOppgaveVellykket::class.simpleName,
                resultat1.getTypedContent<String>("/nyOppgave/__typename")
            )

            val idNyOppgave1 = resultat1.getTypedContent<UUID>("/nyOppgave/id")

            // og varsler blir markert som sendt
            val notifikasjon = queryModel.hentNotifikasjon(idNyOppgave1)!!
            notifikasjon.eksterneVarsler.forEach {
                queryModel.oppdaterModellEtterHendelse(
                    HendelseModel.EksterntVarselVellykket(
                        notifikasjonId = idNyOppgave1,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        hendelseId = UUID.randomUUID(),
                        produsentId = "fager",
                        kildeAppNavn = "test",
                        varselId = it.varselId,
                        råRespons = NullNode.instance,
                    )
                )
            }

            // andre kall er idempotent
            val idNyOppgave2 = client.produsentApi(nyOppgaveReq).getTypedContent<UUID>("/nyOppgave/id")
            assertEquals(idNyOppgave1, idNyOppgave2)
        }
    }

    @Test
    fun `ny kalenderavtale er idempotent også når varsler er sendt`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val queryModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = queryModel
        ) {
            queryModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    merkelapp = "tag",
                    grupperingsid = grupperingsid,
                )
            )
            val req = GraphQLRequest(
                query = nyKalenderavtaleGql("foo"),
                variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser1),
            )
            val resultat1 = client.produsentApi(req)

            // første kall er opprettet
            assertEquals(
                MutationKalenderavtale.NyKalenderavtaleVellykket::class.simpleName,
                resultat1.getTypedContent<String>("/nyKalenderavtale/__typename")
            )

            val notifikasjonId = resultat1.getTypedContent<UUID>("/nyKalenderavtale/id")

            // og varsler blir markert som sendt
            val notifikasjon = queryModel.hentNotifikasjon(notifikasjonId)!!
            notifikasjon.eksterneVarsler.forEach {
                queryModel.oppdaterModellEtterHendelse(
                    HendelseModel.EksterntVarselVellykket(
                        notifikasjonId = notifikasjonId,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        hendelseId = UUID.randomUUID(),
                        produsentId = "fager",
                        kildeAppNavn = "test",
                        varselId = it.varselId,
                        råRespons = NullNode.instance,
                    )
                )
            }

            // andre kall er idempotent
            val id2 = client.produsentApi(req).getTypedContent<UUID>("/nyKalenderavtale/id")
            assertEquals(notifikasjonId, id2)
        }
    }

    @Test
    fun `ny oppgave med endrede varsler får feilmelding`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val resultat1 = client.produsentApi(
                GraphQLRequest(
                    query = nyOppgaveGql("foo"),
                    variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser1),
                )
            )
            val resultat2 = client.produsentApi(
                GraphQLRequest(
                    query = nyOppgaveGql("foo"),
                    variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser2),
                )
            )

            // første kall er opprettet
            assertEquals(
                MutationNyOppgave.NyOppgaveVellykket::class.simpleName,
                resultat1.getTypedContent<String>("/nyOppgave/__typename")
            )
            // andre kall er feilmelding
            assertEquals(
                Error.DuplikatEksternIdOgMerkelapp::class.simpleName,
                resultat2.getTypedContent<String>("/nyOppgave/__typename")
            )
        }
    }

    @Test
    fun `ny kalenderavtale med endrede varsler får feilmelding`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val queryModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = queryModel
        ) {
            queryModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    merkelapp = "tag",
                    grupperingsid = grupperingsid,
                )
            )
            val resultat1 = client.produsentApi(
                GraphQLRequest(
                    query = nyKalenderavtaleGql("foo"),
                    variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser1),
                )
            )
            val resultat2 = client.produsentApi(
                GraphQLRequest(
                    query = nyKalenderavtaleGql("foo"),
                    variables = laxObjectMapper.readValue<Map<String, Any?>>(varlser2),
                )
            )

            // første kall er opprettet
            assertEquals(
                MutationKalenderavtale.NyKalenderavtaleVellykket::class.simpleName,
                resultat1.getTypedContent<String>("/nyKalenderavtale/__typename")
            )
            // andre kall er feilmelding
            assertEquals(
                Error.DuplikatEksternIdOgMerkelapp::class.simpleName,
                resultat2.getTypedContent<String>("/nyKalenderavtale/__typename")
            )
        }
    }

    @Test
    fun `Oppgave med påminnelse med varsel`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            // language=GraphQL
            val oppgaveMedPåminnelse = """
            mutation NyOppgave(${'$'}eksterneVarsler: [PaaminnelseEksterntVarselInput!]! = []) {
                nyOppgave(nyOppgave: {
                    metadata: {
                        eksternId: "$eksternId"
                        virksomhetsnummer: "${mottaker.virksomhetsnummer}"
                    }
                    notifikasjon: {
                        tekst: "foo"
                        merkelapp: "tag"
                        lenke: "#bar"
                    }
                    mottaker: {altinn: {
                        serviceCode: "${mottaker.serviceCode}"
                        serviceEdition: "${mottaker.serviceEdition}"
                    }}
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "${LocalDateTime.now().plusDays(1)}"
                        }
                        eksterneVarsler: ${'$'}eksterneVarsler
                    }
                }) {
                    __typename
                    ... on Error { feilmelding }
                    ... on NyOppgaveVellykket { id }
                }
            }
            """
            val nyOppgaveReq = GraphQLRequest(
                query = oppgaveMedPåminnelse,
                variables = laxObjectMapper.readValue<Map<String, Any?>>(påminnelseVarsler),
            )
            val idNyOppgave1 = client.produsentApi(nyOppgaveReq).getTypedContent<UUID>("/nyOppgave/id")
            val idNyOppgave2 = client.produsentApi(nyOppgaveReq).getTypedContent<UUID>("/nyOppgave/id")

            // er opprettet med samme id
            assertEquals(idNyOppgave2, idNyOppgave1)
        }
    }

}

@Language("json")
private val varlser1 = """
{
  "eksterneVarsler": [
    {
      "sms": {
        "mottaker": {
          "kontaktinfo": {
            "fnr": "1234",
            "tlf": "99999999"
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
            "epostadresse": "foo@bar.baz"
          }
        },
        "epostTittel": "En tittel til din epost",
        "epostHtmlBody": "<body><h1>hei</h1></body>",
        "sendetidspunkt": {
          "tidspunkt": "2021-01-01T12:00:00"
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
          "sendevindu": "LOEPENDE"
        }
      }
    }
  ]
}
"""

@Language("json")
private val varlser2 = """
{
  "eksterneVarsler": [
    {
      "sms": {
        "mottaker": {
          "kontaktinfo": {
            "fnr": "1337",
            "tlf": "40000000"
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
            "epostadresse": "foo@bar.baz"
          }
        },
        "epostTittel": "En tittel til din epost",
        "epostHtmlBody": "<body><h1>hei</h1></body>",
        "sendetidspunkt": {
          "tidspunkt": "2021-01-01T13:00:00"
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
          "sendevindu": "LOEPENDE"
        }
      }
    }
  ]
}
"""

@Language("json")
private val påminnelseVarsler = """
{
  "eksterneVarsler": [
    {
      "sms": {
        "mottaker": {
          "kontaktinfo": {
            "fnr": "1337",
            "tlf": "49999999"
          }
        },
        "smsTekst": "En test SMS",
        "sendevindu": "NKS_AAPNINGSTID"
      }
    },
    {
      "epost": {
        "mottaker": {
          "kontaktinfo": {
            "fnr": "0",
            "epostadresse": "foo@bar.baz"
          }
        },
        "epostTittel": "En tittel til din epost",
        "epostHtmlBody": "<body><h1>hei</h1></body>",
        "sendevindu": "DAGTID_IKKE_SOENDAG"
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
        "sendevindu": "LOEPENDE"
      }
    }
  ]
}
"""

