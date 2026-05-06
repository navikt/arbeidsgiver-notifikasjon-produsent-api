package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.intellij.lang.annotations.Language
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DataproduktEksterVarselVellykketTest {
    private val meta = HendelseMetadata(Instant.now())
    private val altinntjenesteVarselKontaktinfo = HendelseModel.AltinntjenesteVarselKontaktinfo(
        varselId = uuid("1"),
        virksomhetsnummer = "1",
        serviceCode = "1",
        serviceEdition = "1",
        tittel = "hey",
        innhold = "body",
        sendevindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
        sendeTidspunkt = null
    )

    @Test
    fun `Dataprodukt ekstern varsel vellykket med flere mottakere i respons`() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
        val subject = DataproduktModel(database)
        subject.oppdaterModellEtterHendelse(
            EksempelHendelse.BeskjedOpprettet.copy(
                eksterneVarsler = listOf(
                    altinntjenesteVarselKontaktinfo
                )
            ), meta
        )
        subject.oppdaterModellEtterHendelse(
            EksempelHendelse.EksterntVarselVellykket.copy(
                varselId = altinntjenesteVarselKontaktinfo.varselId,
                råRespons = laxObjectMapper.readTree(vellykketRespons)
            ), meta
        )

        val result = database.nonTransactionalExecuteQuery(
            """
                select * from ekstern_varsel_resultat where varsel_id = ?
                """,
            {
                uuid(altinntjenesteVarselKontaktinfo.varselId)
            }
        ) {
            mapOf(
                "resultat_name" to this.getString("resultat_name"),
                "resultat_receiver" to this.getString("resultat_receiver"),
                "resultat_type" to this.getString("resultat_type"),
            )
        }

        // lagrer ett innslag per endPointResult
        assertEquals(
            listOf(
                mapOf(
                    "resultat_name" to "MALMEFJORDEN OG RIDABU REGNSKAP",
                    "resultat_receiver" to "post@firma.no",
                    "resultat_type" to "EMAIL",
                ),
                mapOf(
                    "resultat_name" to "JORDSMONN ULOGISK",
                    "resultat_receiver" to "12341234",
                    "resultat_type" to "SMS",
                ),
                mapOf(
                    "resultat_name" to "TORN KONSEKVENT",
                    "resultat_receiver" to "KONSEKVENT.TORN2@foo.net",
                    "resultat_type" to "EMAIL",
                ),
            ),
            result
        )
    }

    @Test
    fun `Dataprodukt ekstern varsel vellykket med Altinn 3 legacy notifications respons`() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
        val subject = DataproduktModel(database)
        subject.oppdaterModellEtterHendelse(
            EksempelHendelse.BeskjedOpprettet.copy(
                eksterneVarsler = listOf(
                    altinntjenesteVarselKontaktinfo
                )
            ), meta
        )
        subject.oppdaterModellEtterHendelse(
            EksempelHendelse.EksterntVarselVellykket.copy(
                varselId = altinntjenesteVarselKontaktinfo.varselId,
                råRespons = laxObjectMapper.readTree(altinn3LegacyRespons)
            ), meta
        )

        val result = database.nonTransactionalExecuteQuery(
            """
                select * from ekstern_varsel_resultat where varsel_id = ?
                """,
            {
                uuid(altinntjenesteVarselKontaktinfo.varselId)
            }
        ) {
            mapOf(
                "resultat_name" to this.getString("resultat_name"),
                "resultat_receiver" to this.getString("resultat_receiver"),
                "resultat_type" to this.getString("resultat_type"),
            )
        }

        assertEquals(
            listOf(
                mapOf(
                    "resultat_name" to "Delivered",
                    "resultat_receiver" to "foo@foo.no",
                    "resultat_type" to "EMAIL",
                ),
            ),
            result
        )
    }

    @Test
    fun `Dataprodukt ekstern varsel vellykket med Altinn 3 recipients respons`() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
        val subject = DataproduktModel(database)
        subject.oppdaterModellEtterHendelse(
            EksempelHendelse.BeskjedOpprettet.copy(
                eksterneVarsler = listOf(
                    altinntjenesteVarselKontaktinfo
                )
            ), meta
        )
        subject.oppdaterModellEtterHendelse(
            EksempelHendelse.EksterntVarselVellykket.copy(
                varselId = altinntjenesteVarselKontaktinfo.varselId,
                råRespons = laxObjectMapper.readTree(altinn3RecipientsRespons)
            ), meta
        )

        val result = database.nonTransactionalExecuteQuery(
            """
                select * from ekstern_varsel_resultat where varsel_id = ?
                """,
            {
                uuid(altinntjenesteVarselKontaktinfo.varselId)
            }
        ) {
            mapOf(
                "resultat_name" to this.getString("resultat_name"),
                "resultat_receiver" to this.getString("resultat_receiver"),
                "resultat_type" to this.getString("resultat_type"),
            )
        }

        assertEquals(
            listOf(
                mapOf(
                    "resultat_name" to "Email_Delivered",
                    "resultat_receiver" to "bar@foo.no",
                    "resultat_type" to "EMAIL",
                ),
                mapOf(
                    "resultat_name" to "Email_Delivered",
                    "resultat_receiver" to "foo@foo.kommune.no",
                    "resultat_type" to "EMAIL",
                ),
                mapOf(
                    "resultat_name" to "Email_Delivered",
                    "resultat_receiver" to "foo@fpp.kommune.no",
                    "resultat_type" to "EMAIL",
                ),
                mapOf(
                    "resultat_name" to "Email_Delivered",
                    "resultat_receiver" to "asdf@asdfasdf.kommune.no",
                    "resultat_type" to "EMAIL",
                ),
                mapOf(
                    "resultat_name" to "Email_Delivered",
                    "resultat_receiver" to "asdfasdfasdf@asdf.kommune.no",
                    "resultat_type" to "EMAIL",
                ),
                mapOf(
                    "resultat_name" to "Email_Delivered",
                    "resultat_receiver" to "postasdf@asdfasdf.kommune.no",
                    "resultat_type" to "EMAIL",
                ),
            ),
            result
        )
    }
}

@Language("JSON")
const val altinn3LegacyRespons = """
[
  {
    "generated": 0,
    "notifications": [],
    "orderId": "998db3c9-0de8-437c-bf11-6b2dd9f21ef3",
    "succeeded": 0
  },
  {
    "generated": 1,
    "notifications": [
      {
        "id": "1ffda27d-e760-4afc-a0ed-178119645300",
        "recipient": {
          "emailAddress": "foo@foo.no"
        },
        "sendStatus": {
          "description": "The email was delivered to the recipient. No errors reported, making it likely it was received by the recipient.",
          "lastUpdate": "2025-11-18T11:35:42.688347Z",
          "status": "Delivered"
        },
        "succeeded": true
      }
    ],
    "orderId": "998db3c9-0de8-437c-bf11-6b2dd9f21ef3",
    "succeeded": 1
  }
]
"""

@Language("JSON")
const val altinn3RecipientsRespons = """
{
  "lastUpdate": "2026-05-06T07:32:53.63865Z",
  "recipients": [
    {
      "destination": "bar@foo.no",
      "lastUpdate": "2026-05-06T07:32:25.615858Z",
      "status": "Email_Delivered",
      "type": "Email"
    },
    {
      "destination": "foo@foo.kommune.no",
      "lastUpdate": "2026-05-06T07:32:09.504019Z",
      "status": "Email_Delivered",
      "type": "Email"
    },
    {
      "destination": "foo@fpp.kommune.no",
      "lastUpdate": "2026-05-06T07:32:09.386348Z",
      "status": "Email_Delivered",
      "type": "Email"
    },
    {
      "destination": "asdf@asdfasdf.kommune.no",
      "lastUpdate": "2026-05-06T07:32:50.147282Z",
      "status": "Email_Delivered",
      "type": "Email"
    },
    {
      "destination": "asdfasdfasdf@asdf.kommune.no",
      "lastUpdate": "2026-05-06T07:32:53.216275Z",
      "status": "Email_Delivered",
      "type": "Email"
    },
    {
      "destination": "postasdf@asdfasdf.kommune.no",
      "lastUpdate": "2026-05-06T07:32:53.63865Z",
      "status": "Email_Delivered",
      "type": "Email"
    }
  ],
  "shipmentId": "2abd417f-fe84-4c26-9958-7537dcdecc55",
  "status": "Order_Completed",
  "type": "Notification"
}
"""

@Suppress("HttpUrlsUsage")
@Language("JSON")
const val vellykketRespons = """
{
  "notificationResult": [
    {
      "endPoints": {
        "nil": false,
        "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}EndPoints",
        "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.NotificationResult",
        "value": {
          "endPointResult": [
            {
              "name": {
                "nil": false,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}Name",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": "MALMEFJORDEN OG RIDABU REGNSKAP",
                "globalScope": false,
                "declaredType": "java.lang.String",
                "typeSubstituted": false
              },
              "transportType": "EMAIL",
              "receiverAddress": {
                "nil": false,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}ReceiverAddress",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": "post@firma.no",
                "globalScope": false,
                "declaredType": "java.lang.String",
                "typeSubstituted": false
              },
              "retrieveFromProfile": {
                "nil": true,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}RetrieveFromProfile",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": null,
                "globalScope": false,
                "declaredType": "java.lang.Boolean",
                "typeSubstituted": false
              }
            },
            {
              "name": {
                "nil": false,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}Name",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": "JORDSMONN ULOGISK",
                "globalScope": false,
                "declaredType": "java.lang.String",
                "typeSubstituted": false
              },
              "transportType": "SMS",
              "receiverAddress": {
                "nil": false,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}ReceiverAddress",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": "12341234",
                "globalScope": false,
                "declaredType": "java.lang.String",
                "typeSubstituted": false
              },
              "retrieveFromProfile": {
                "nil": true,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}RetrieveFromProfile",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": null,
                "globalScope": false,
                "declaredType": "java.lang.Boolean",
                "typeSubstituted": false
              }
            },
            {
              "name": {
                "nil": false,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}Name",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": "TORN KONSEKVENT",
                "globalScope": false,
                "declaredType": "java.lang.String",
                "typeSubstituted": false
              },
              "transportType": "EMAIL",
              "receiverAddress": {
                "nil": false,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}ReceiverAddress",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": "KONSEKVENT.TORN2@foo.net",
                "globalScope": false,
                "declaredType": "java.lang.String",
                "typeSubstituted": false
              },
              "retrieveFromProfile": {
                "nil": true,
                "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}RetrieveFromProfile",
                "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResult",
                "value": null,
                "globalScope": false,
                "declaredType": "java.lang.Boolean",
                "typeSubstituted": false
              }
            }
          ]
        },
        "globalScope": false,
        "declaredType": "no.altinn.schemas.services.serviceengine.notification._2015._06.EndPointResultList",
        "typeSubstituted": false
      },
      "reporteeNumber": {
        "nil": false,
        "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}ReporteeNumber",
        "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.NotificationResult",
        "value": "987654321",
        "globalScope": false,
        "declaredType": "java.lang.String",
        "typeSubstituted": false
      },
      "notificationType": {
        "nil": false,
        "name": "{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}NotificationType",
        "scope": "no.altinn.schemas.services.serviceengine.notification._2015._06.NotificationResult",
        "value": "TokenTextOnly",
        "globalScope": false,
        "declaredType": "java.lang.String",
        "typeSubstituted": false
      }
    }
  ]
}
"""