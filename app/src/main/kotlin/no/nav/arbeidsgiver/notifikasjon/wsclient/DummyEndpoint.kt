package no.nav.arbeidsgiver.notifikasjon.wsclient

import jakarta.jws.WebMethod
import jakarta.jws.WebService
import jakarta.xml.ws.Endpoint

fun main() {
    Endpoint.publish(
        "http://localhost:9000/ServiceEngineExternal/NotificationAgencyExternalBasic.svc",
        @WebService(name = "INotificationAgencyExternalBasic",
            targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        object : INotificationAgencyExternalBasic {
            override fun test() {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasic(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?,
            ) {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasicV2(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?,
            ): String {
                TODO("Not yet implemented")
            }

            @WebMethod(operationName = "SendStandaloneNotificationBasicV3",
                action = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10/INotificationAgencyExternalBasic/SendStandaloneNotificationBasicV3")
            override fun sendStandaloneNotificationBasicV3(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?,
            ): SendNotificationResultList {
                return SendNotificationResultList().apply {
                    notificationResult = listOf(
                        NotificationResult().apply {
                            reporteeNumber = ns("ReporteeNumber", "42")
                        }
                    )
                }
            }
        }
    )
}