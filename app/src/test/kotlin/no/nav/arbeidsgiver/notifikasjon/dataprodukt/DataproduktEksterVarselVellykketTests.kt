package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant

class DataproduktEksterVarselVellykketTests : DescribeSpec({
    val meta = HendelseMetadata(Instant.now())
    val altinntjenesteVarselKontaktinfo = HendelseModel.AltinntjenesteVarselKontaktinfo(
        varselId = uuid("1"),
        virksomhetsnummer = "1",
        serviceCode = "1",
        serviceEdition = "1",
        tittel = "hey",
        innhold = "body",
        sendevindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
        sendeTidspunkt = null
    )

    describe("Dataprodukt ekstern varsel vellykket med flere mottakere i respons") {
        val database = testDatabase(Dataprodukt.databaseConfig)
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

        it("lagrer ett innslag per endPointResult") {
            result shouldHaveSize 3
            result shouldContainExactlyInAnyOrder  listOf(
                mapOf(
                "resultat_name" to "MALMEFJORDEN OG RIDABU REGNSKAP",
                "resultat_receiver" to "post@firma.no",
                "resultat_type" to "EMAIL",
                ),
                mapOf(
                "resultat_name" to "JORDSMONN ULOGISK ",
                "resultat_receiver" to "12341234",
                "resultat_type" to "SMS",
                ),
                mapOf(
                "resultat_name" to "TORN KONSEKVENT ",
                "resultat_receiver" to "KONSEKVENT.TORN2@foo.net",
                "resultat_type" to "EMAIL",
                ),
            )
        }
    }
})

val vellykketRespons = """
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
                "value": "JORDSMONN ULOGISK ",
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
                "value": "TORN KONSEKVENT ",
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
        "value": "810825472",
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