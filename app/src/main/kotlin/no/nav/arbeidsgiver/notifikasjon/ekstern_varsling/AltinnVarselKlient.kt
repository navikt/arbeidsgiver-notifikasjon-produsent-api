package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType
import no.altinn.schemas.services.serviceengine.notification._2009._10.*
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2015._06.Service
import no.altinn.services.common.fault._2009._10.AltinnFault
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasic
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.blockingIO
import org.apache.cxf.ext.logging.LoggingInInterceptor
import org.apache.cxf.ext.logging.LoggingOutInterceptor
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName


interface AltinnVarselKlient {

    sealed interface AltinnResponse {
        val rå: JsonNode

        data class Ok(
            override val rå: JsonNode
        ) : AltinnResponse

        data class Feil(
            override val rå: JsonNode,
            val feilkode: String,
            val feilmelding: String,
        ) : AltinnResponse
    }


    suspend fun send(eksternVarsel: EksternVarsel): Result<AltinnResponse>

    suspend fun sendSms(
        mottaker: AltinnMottaker,
        tekst: String
    ): Result<AltinnResponse>

    suspend fun sendSms(
        mobilnummer: String,
        virksomhetsnummer: String,
        tekst: String
    ): Result<AltinnResponse>

    suspend fun sendEpost(
        mottaker: AltinnMottaker,
        tittel: String,
        tekst: String
    ): Result<AltinnResponse>

    suspend fun sendEpost(
        virksomhetsnummer: String,
        epostadresse: String,
        tittel: String,
        tekst: String
    ): Result<AltinnResponse>
}

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
class AltinnVarselKlientImpl(
    altinnEndPoint: String = basedOnEnv(
        prod = "",
        other = "https://tt02.altinn.no/ServiceEngineExternal/NotificationAgencyExternalBasic.svc"
    ),
    private val altinnBrukernavn: String = System.getenv("ALTINN_BASIC_WS_BRUKERNAVN") ?: "",
    private val altinnPassord: String = System.getenv("ALTINN_BASIC_WS_PASSORD") ?: "",
): AltinnVarselKlient {
    val log = logger()
    private val wsclient = createServicePort(altinnEndPoint, INotificationAgencyExternalBasic::class.java)


    override suspend fun send(eksternVarsel: EksternVarsel): Result<AltinnVarselKlient.AltinnResponse> {
        return when (eksternVarsel) {
            is EksternVarsel.Epost -> sendEpost(
                virksomhetsnummer = eksternVarsel.fnrEllerOrgnr,
                epostadresse = eksternVarsel.epostadresse,
                tittel = eksternVarsel.tittel,
                tekst = eksternVarsel.body,
            )
            is EksternVarsel.Sms -> sendSms(
                virksomhetsnummer = eksternVarsel.fnrEllerOrgnr,
                mobilnummer = eksternVarsel.mobilnummer,
                tekst = eksternVarsel.tekst,
            )
        }
    }

    override suspend fun sendSms(
        mottaker: AltinnMottaker,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")
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
                        },
                        TextToken().apply {
                            tokenValue = ns("TokenValue", "")
                        }
                    )
                )
                useServiceOwnerShortNameAsSenderOfSms = ns("UseServiceOwnerShortNameAsSenderOfSms", true)
            }
        ))
    }

    override suspend fun sendSms(
        mobilnummer: String,
        virksomhetsnummer: String,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")

                reporteeNumber = ns("ReporteeNumber", virksomhetsnummer)
                receiverEndPoints = ns("ReceiverEndPoints",
                    ReceiverEndPointBEList().withReceiverEndPoint(
                        ReceiverEndPoint().apply {
                            transportType = ns("TransportType", TransportType.SMS)
                            receiverAddress = ns("ReceiverAddress", mobilnummer)
                        }
                    )
                )

                textTokens = ns("TextTokens",
                    TextTokenSubstitutionBEList().withTextToken(
                        TextToken().apply {
                            tokenValue = ns("TokenValue", tekst)
                        },
                        TextToken().apply {
                            tokenValue = ns("TokenValue", "")
                        }
                    )
                )
                useServiceOwnerShortNameAsSenderOfSms = ns("UseServiceOwnerShortNameAsSenderOfSms", true)
            }
        ))
    }

    override suspend fun sendEpost(
        mottaker: AltinnMottaker,
        tittel: String,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")
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
        ))
    }

    override suspend fun sendEpost(
        virksomhetsnummer: String,
        epostadresse: String,
        tittel: String,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")

                reporteeNumber = ns("ReporteeNumber", virksomhetsnummer)
                receiverEndPoints = ns("ReceiverEndPoints",
                    ReceiverEndPointBEList().withReceiverEndPoint(
                        ReceiverEndPoint().apply {
                            transportType = ns("TransportType", TransportType.EMAIL)
                            receiverAddress = ns("ReceiverAddress", epostadresse)
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
        ))
    }

    private suspend fun send(payload: StandaloneNotificationBEList): Result<AltinnVarselKlient.AltinnResponse> {
        return blockingIO {
            try {
                val response = wsclient.sendStandaloneNotificationBasicV3(
                    altinnBrukernavn,
                    altinnPassord,
                    payload
                )
                Result.success(
                    AltinnVarselKlient.AltinnResponse.Ok(
                        rå = objectMapper.valueToTree(response),
                    )
                )
            } catch (e: INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage) {
                log.error(
                    "Feil fra altinn ved sending av notifikasjon: ${e.message}, ${e.faultInfo.toLoggableString()}",
                    e
                )
                Result.success(
                    AltinnVarselKlient.AltinnResponse.Feil(
                        feilkode = e.faultInfo.errorID.toString(),
                        feilmelding = e.faultInfo.altinnErrorMessage.value,
                        rå = objectMapper.valueToTree(e),
                    )
                )
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
    }

//    fun testEksternVarsel() {
//        funker
//        sendEpost(
//            mottaker = AltinnMottaker(serviceCode = "4936", serviceEdition = "1", virksomhetsnummer = "910825526"),
//            tittel = "Dette er en test av ekstern varseltjeneste",
//            tekst = "<h1>Obs</h1><br /> <p>Dette er en <strong>bare</strong> en test."
//        )

//        feiler når virksomhet ikke har sms adresse oppgitt i kofuvi selv om dette finnes på tjeneste
//        sendSms(
//            mottaker = AltinnMottaker(serviceCode = "4936", serviceEdition = "1", virksomhetsnummer = "910825526"),
//            "Dette er en test av ekstern varseltjeneste"
//        )

//        funker
//        sendEpost(
//            epostadresse = "ken.gullaksen@nav.no",
//            virksomhetsnummer = "910825526",
//            tittel = "Dette er en test av ekstern varseltjeneste",
//            tekst = "<h1>Obs</h1><br /> <p>Dette er en <strong>bare</strong> en test."
//        )

//        funker
//        sendSms(
//            mobilnummer = "47239082",
//            virksomhetsnummer = "910825526",
//            "Dette er en test av ekstern varseltjeneste"
//        )

//    }
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

fun StandaloneNotification.setMottaker(mottaker: AltinnMottaker) {
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