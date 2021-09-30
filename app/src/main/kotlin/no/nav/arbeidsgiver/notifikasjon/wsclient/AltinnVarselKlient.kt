package no.nav.arbeidsgiver.notifikasjon.wsclient

import jakarta.xml.bind.JAXBElement
import jakarta.xml.ws.BindingProvider
import jakarta.xml.ws.handler.MessageContext
import jakarta.xml.ws.handler.soap.SOAPHandler
import jakarta.xml.ws.handler.soap.SOAPMessageContext
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.io.ByteArrayOutputStream
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
    val log = logger()
    private val wsdl = javaClass.getResource("/META-INF/wsdl/NotificationAgencyExternalBasic.svc.wsdl")!!
    private val wsclient =
        NotificationAgencyExternalBasicSF(wsdl).basicHttpBindingINotificationAgencyExternalBasic.apply {
            if (this is BindingProvider) {
                this.requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] = basedOnEnv(
                    prod = "",
                    other = "https://tt02.altinn.no/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
//                    other = "http://localhost:9000/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
                )
                addRequestResponseLogging()
            }
        }

    fun testEksternVarsel() {
        sendSms(
            tekst = "hallaisen, mctysen. dette er en test",
            mottaker = AltinnMottaker(
                serviceCode = "4936",
                serviceEdition = "1",
                virksomhetsnummer = "910825526"
            )
        )
        sendEpost(
            tittel = "tjobing",
            tekst = "<h1>hei</h1><br /> <p>Dette er en <strong>test!</strong>",
            mottaker = AltinnMottaker(
                serviceCode = "4936",
                serviceEdition = "1",
                virksomhetsnummer = "910825526"
            )
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
            //log.error("Feil fra altinn ved sending av notifikasjon: ${e.message}, ${e.faultInfo.toLoggableString()}", e)
            throw Error("Feil fra altinn ved sending av notifikasjon: ${e.message}, ${e.faultInfo.toLoggableString()}", e)
        }
    }
}

fun BindingProvider.addRequestResponseLogging() {
    val handlerChain = binding.handlerChain
    handlerChain.add(object : SOAPHandler<SOAPMessageContext> {
        override fun handleMessage(context: SOAPMessageContext): Boolean {
            //val isRequest : Boolean = context[MessageContext.MESSAGE_OUTBOUND_PROPERTY] as Boolean
            val baos = ByteArrayOutputStream()
            context.message.writeTo(baos)
            logger().info(
                String(baos.toByteArray())
                    .replace(Regex("(systemUserName>|systemPassword>)(.*?)(</.+?:systemUserName>|</.+?:systemPassword)"), "$1***$3")
                    .replace(Regex("""(^|\W)\d{11}(?=$|\W)"""), "$1***********")
            )
            return true
        }

        override fun handleFault(context: SOAPMessageContext): Boolean {
            val baos = ByteArrayOutputStream()
            context.message.writeTo(baos)
            logger().info(String(baos.toByteArray()))
            return true
        }

        override fun close(context: MessageContext?) {}

        override fun getHeaders(): MutableSet<QName> {
            return mutableSetOf()
        }
    })
    binding.handlerChain = handlerChain
}

fun AltinnFault.toLoggableString(): String {
    return """
        altinnErrorMessage=${altinnErrorMessage.value}
        altinnExtendedErrorMessage=${altinnExtendedErrorMessage.value}
        altinnLocalizedErrorMessage=${altinnLocalizedErrorMessage.value}
        errorGuid=${errorGuid.value}
        errorID=${errorID}
        userGuid=${userGuid.value}
        userId=${userId.value}
    """.trimIndent()
}

fun StandaloneNotificationBEList.withEmail(
    mottaker: AltinnMottaker,
    tekst: String,
    tittel: String,
): StandaloneNotificationBEList {
    getStandaloneNotification().add(
        StandaloneNotification().apply {
            withMottaker(mottaker)
            receiverEndPoints = ns("ReceiverEndPoints", ReceiverEndPointBEList().apply {
                receiverEndPoint = listOf(
                    ReceiverEndPoint().apply {
                        transportType = ns("TransportType", TransportType.EMAIL)
                    }
                )
            })
            textTokens = ns("TextTokens", TextTokenSubstitutionBEList().apply {
                textToken = listOf(
                    TextToken().apply {
                        tokenValue = ns("TokenValue", tittel)
                    },
                    TextToken().apply {
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
    getStandaloneNotification().add(
        StandaloneNotification().apply {
            withMottaker(mottaker)
            receiverEndPoints = ns("ReceiverEndPoints", ReceiverEndPointBEList().apply {
                receiverEndPoint = listOf(
                    ReceiverEndPoint().apply {
                        transportType = ns("TransportType", TransportType.SMS)
                    }
                )
            })
            textTokens = ns("TextTokens", TextTokenSubstitutionBEList().apply {
                textToken = listOf(
                    TextToken().apply {
                        tokenValue = ns("TokenValue", tekst)
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
