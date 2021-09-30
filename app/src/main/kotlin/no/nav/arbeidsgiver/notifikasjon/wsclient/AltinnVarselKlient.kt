package no.nav.arbeidsgiver.notifikasjon.wsclient

import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType
import no.altinn.schemas.services.serviceengine.notification._2009._10.*
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2015._06.Service
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasic
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import org.apache.cxf.ext.logging.LoggingInInterceptor
import org.apache.cxf.ext.logging.LoggingOutInterceptor
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import javax.xml.bind.JAXBElement
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
    altinnEndPoint: String = basedOnEnv(
        prod = "",
        other = "https://tt02.altinn.no/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
    ),
    private val altinnBrukernavn: String = System.getenv("ALTINN_BASIC_WS_BRUKERNAVN") ?: "",
    private val altinnPassord: String = System.getenv("ALTINN_BASIC_WS_PASSORD") ?: "",
) {
    private val wsclient = createServicePort(altinnEndPoint, INotificationAgencyExternalBasic::class.java)

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
    return withStandaloneNotification(
        StandaloneNotification().apply {
            setMottaker(mottaker)

            receiverEndPoints = ns("ReceiverEndPoints",
                ReceiverEndPointBEList().withReceiverEndPoint(
                    ReceiverEndPoint().apply {
                        transportType = ns("TransportType", TransportType.EMAIL)
                    }
                )
            )

            textTokens = ns("TextTokens",
                TextTokenSubstitutionBEList().withTextToken(
                    TextToken().apply {
                        tokenValue = ns("TokenValue", tittel)
                    },
                    TextToken().apply {
                        tokenValue = ns("TokenValue", tekst)
                    }
                )
            )
            fromAddress = ns("FromAddress", "ikke-svar@nav.no")
        }
    )
}

fun StandaloneNotificationBEList.withSms(
    mottaker: AltinnMottaker,
    tekst: String,
): StandaloneNotificationBEList {
    return withStandaloneNotification(
        StandaloneNotification().apply {
            setMottaker(mottaker)

            receiverEndPoints = ns("ReceiverEndPoints",
                ReceiverEndPointBEList().withReceiverEndPoint(
                    ReceiverEndPoint().apply {
                        transportType = ns("TransportType", TransportType.SMS)
                    }
                )
            )

            textTokens = ns("TextTokens",
                TextTokenSubstitutionBEList().withTextToken(
                    TextToken().apply {
                        tokenValue = ns("TokenValue", tekst)
                    }
                )
            )
            useServiceOwnerShortNameAsSenderOfSms = ns("UseServiceOwnerShortNameAsSenderOfSms", true)
        }
    )
}

fun StandaloneNotification.setMottaker(mottaker: AltinnMottaker) {
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

fun <PORT_TYPE> createServicePort(
    url: String,
    clazz: Class<PORT_TYPE>,
): PORT_TYPE = JaxWsProxyFactoryBean().apply {
    address = url
    serviceClass = clazz
    inInterceptors.add(LoggingInInterceptor().apply {
        addSensitiveElementNames(setOf("systemUserName", "systemPassword", "ns2:ReporteeNumber"))
    })
    outInterceptors.add(LoggingOutInterceptor().apply {
        addSensitiveElementNames(setOf("systemUserName", "systemPassword", "ns2:ReporteeNumber"))
    })
}.create(clazz)