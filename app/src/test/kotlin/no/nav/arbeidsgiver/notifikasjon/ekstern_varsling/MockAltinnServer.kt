package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.altinn.schemas.services.serviceengine.notification._2015._06.NotificationResult
import no.altinn.schemas.services.serviceengine.notification._2015._06.SendNotificationResultList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2022._11.StandaloneNotificationBEListV2
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
                return SendNotificationResultList().apply {
                    notificationResult.add(NotificationResult())
                }
            }

            override fun sendStandaloneNotificationBasicV4(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEListV2?
            ): SendNotificationResultList {
                TODO("Not yet implemented")
            }
        }
    }.create()
}