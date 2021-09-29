package no.nav.arbeidsgiver.notifikasjon.wsclient

import jakarta.xml.bind.JAXBElement
import jakarta.xml.ws.BindingProvider
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import javax.xml.namespace.QName


/**
 * TokenTextOnly NotificationType (aka varslingsmal) i altinn har visstnok forskjellig oppførsel basert på
 * [TransportType]. Ved [TransportType.EMAIL] så kan det settes tittel,
 * dette kan ikke settes ved [TransportType.SMS]. Følgelig kan man ikke bruke [TransportType.BOTH] med denne malen.
 * Det er også forskjell på hvilken token som benyttes som innhold ved de forskjellige transporttypene:
 * [TransportType.SMS] => {token_0:melding, token_1: blank}
 * [TransportType.EMAIL] => {token_0:tittel, token_1: innhold}
 *
 * [TransportType.EMAIL] støtter også html, det gjør ikke [TransportType.SMS]
 */
class AltinnVarselKlient(
    private val altinnBrukernavn: String = System.getenv("ALTINN_BASIC_WS_BRUKERNAVN") ?: "",
    private val altinnPassord: String = System.getenv("ALTINN_BASIC_WS_PASSORD") ?: "",
) {
    private val wsdl = javaClass.getResource("/META-INF/wsdl/NotificationAgencyExternalBasic.svc.wsdl")!!
    private val wsclient = NotificationAgencyExternalBasicSF(wsdl).basicHttpBindingINotificationAgencyExternalBasic.apply {
        if (this is BindingProvider) {
            this.requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] = basedOnEnv(
                prod = "",
                other = "https://tt02.altinn.no/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
//                other = "http://localhost:9000/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
            )
        }
    }

    fun testEksternVarsel() {
        sendSms(
            mottaker = AltinnMottaker(serviceCode = "4936", serviceEdition = "1", virksomhetsnummer = "910825526"),
            "hallaisen, mctysen. dette er en test"
        )
        sendEpost(
            mottaker = AltinnMottaker(serviceCode = "4936", serviceEdition = "1", virksomhetsnummer = "910825526"),
            tittel = "tjobing",
            tekst = "<h1>hei</h1><br /> <p>Dette er en <strong>test!</strong>"
        )
    }

    fun sendSms(
        mottaker: AltinnMottaker,
        tekst: String,
    ) {
        send(StandaloneNotificationBEList().withSms(mottaker, tekst))
    }

    fun sendEpost(
        mottaker: AltinnMottaker,
        tittel: String,
        tekst: String,
    ) {
        send(StandaloneNotificationBEList().withEmail(mottaker, tekst, tittel))
    }

    private fun send(payload: StandaloneNotificationBEList) {
        try {
            wsclient.sendStandaloneNotificationBasicV3(
                altinnBrukernavn,
                altinnPassord,
                payload
            )
        } catch (e: INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage) {
            throw Error("Feil fra altinn ved sending av notifikasjon: ${e.faultInfo}: ${e.message}", e)
        }
    }
}

fun StandaloneNotificationBEList.withEmail(
    mottaker: AltinnMottaker,
    tekst: String,
    tittel: String,
): StandaloneNotificationBEList {
    standaloneNotification = listOf(
        StandaloneNotification().apply {
            withMottaker(mottaker)
            receiverEndPoints = ns("ReceiverEndPoints", ReceiverEndPointBEList().apply {
                receiverEndPoint = listOf(
                    ReceiverEndPoint().apply {
                        transportType = ns("TransportType", TransportType.EMAIL)
                    }
                )
            }
            )
            textTokens = ns("TextTokens", TextTokenSubstitutionBEList().apply {
                textToken = listOf(
                    TextToken().apply {
                        tokenNum = 0
                        tokenValue = ns("TokenValue", tittel)
                    }
                )
                textToken = listOf(
                    TextToken().apply {
                        tokenNum = 1
                        tokenValue = ns("TokenValue", tekst)
                    }
                )
            })
            fromAddress = ns("FromAddress", "ikke-svar@nav.no")
        })
    return this
}

fun StandaloneNotificationBEList.withSms(
    mottaker: AltinnMottaker,
    tekst: String,
): StandaloneNotificationBEList {
    standaloneNotification = listOf(
        StandaloneNotification().apply {
            withMottaker(mottaker)
            receiverEndPoints = ns("ReceiverEndPoints", ReceiverEndPointBEList().apply {
                receiverEndPoint = listOf(
                    ReceiverEndPoint().apply {
                        transportType = ns("TransportType", TransportType.SMS)
                    }
                )
            }
            )
            textTokens = ns("TextTokens", TextTokenSubstitutionBEList().apply {
                textToken = listOf(
                    TextToken().apply {
                        tokenNum = 0
                        tokenValue = ns("TokenValue", tekst)
                    }
                )
                textToken = listOf(
                    TextToken().apply {
                        tokenNum = 1
                        tokenValue = ns("TokenValue", "")
                    }
                )
            })
            useServiceOwnerShortNameAsSenderOfSms = ns("UseServiceOwnerShortNameAsSenderOfSms", true)
        })
    return this
}

private fun StandaloneNotification.withMottaker(mottaker: AltinnMottaker) {
    languageID = 1044
    notificationType = ns("NotificationType", "TokenTextOnly")
    reporteeNumber = ns("ReporteeNumber", mottaker.virksomhetsnummer)
    service = ns("Service", Service().apply {
        serviceCode = mottaker.serviceCode
        serviceEdition = mottaker.serviceEdition.toInt()
    })
}

@Suppress("HttpUrlsUsage")
inline fun <reified T> ns(localpart: String, value: T): JAXBElement<T> {
    val ns = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10"
    return JAXBElement(QName(ns, localpart), T::class.java, value)
}

//
//fun main() {
//    Endpoint.publish(
//        "http://localhost:9000/ServiceEngineExternal/NotificationAgencyExternalBasic.svc",
//        @WebService(name = "INotificationAgencyExternalBasic", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
//        object : INotificationAgencyExternalBasic {
//            override fun test() {
//                TODO("Not yet implemented")
//            }
//
//            override fun sendStandaloneNotificationBasic(
//                systemUserName: String?,
//                systemPassword: String?,
//                standaloneNotifications: StandaloneNotificationBEList?,
//            ) {
//                TODO("Not yet implemented")
//            }
//
//            override fun sendStandaloneNotificationBasicV2(
//                systemUserName: String?,
//                systemPassword: String?,
//                standaloneNotifications: StandaloneNotificationBEList?,
//            ): String {
//                TODO("Not yet implemented")
//            }
//
//            @WebMethod(operationName = "SendStandaloneNotificationBasicV3", action = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10/INotificationAgencyExternalBasic/SendStandaloneNotificationBasicV3")
//            override fun sendStandaloneNotificationBasicV3(
//                systemUserName: String?,
//                systemPassword: String?,
//                standaloneNotifications: StandaloneNotificationBEList?,
//            ): SendNotificationResultList {
//                TODO("Not yet implemented")
//            }
//        }
//    )
//
//    AltinnVarselKlient().testEksternVarsel()
//}