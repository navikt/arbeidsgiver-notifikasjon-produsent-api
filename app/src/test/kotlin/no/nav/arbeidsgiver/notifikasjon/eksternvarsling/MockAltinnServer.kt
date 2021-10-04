package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

import no.altinn.schemas.services.serviceengine.notification._2015._06.NotificationResult
import no.altinn.schemas.services.serviceengine.notification._2015._06.SendNotificationResultList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasic
import org.apache.cxf.jaxws.JaxWsServerFactoryBean

const val ALTINN_ENDPOINT = "http://localhost:9000/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
fun main() {
    JaxWsServerFactoryBean().apply {
        serviceClass = INotificationAgencyExternalBasic::class.java
        address = ALTINN_ENDPOINT
        serviceBean = object : INotificationAgencyExternalBasic {
            override fun test() {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasic(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?
            ) {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasicV2(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?
            ): String {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasicV3(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?
            ): SendNotificationResultList {
                return SendNotificationResultList().withNotificationResult(
                    NotificationResult()
                )
            }
        }
    }.create()
}