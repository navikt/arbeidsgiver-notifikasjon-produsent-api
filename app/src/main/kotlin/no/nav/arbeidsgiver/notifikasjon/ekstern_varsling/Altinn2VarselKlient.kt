package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.util.logging.*
import jakarta.xml.bind.JAXBElement
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType
import no.altinn.schemas.services.serviceengine.notification._2009._10.*
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2015._06.Service
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasic
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.IdentityProvider
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthConfig
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.blockingIO
import org.apache.cxf.ext.logging.LoggingInInterceptor
import org.apache.cxf.ext.logging.LoggingOutInterceptor
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.message.Message
import org.apache.cxf.phase.AbstractPhaseInterceptor
import org.apache.cxf.phase.Phase
import org.apache.cxf.transport.http.HTTPException
import javax.xml.namespace.QName
import kotlin.time.Duration.Companion.milliseconds


interface Altinn2VarselKlient {
    suspend fun send(eksternVarsel: EksternVarsel): AltinnVarselKlientResponseOrException
}

class Altinn2VarselKlientLogging : Altinn2VarselKlient {
    private val log = logger()

    override suspend fun send(eksternVarsel: EksternVarsel): AltinnVarselKlientResponseOrException {
        log.info("send($eksternVarsel)")
        return AltinnVarselKlientResponse.Ok(
            rå = NullNode.instance
        )
    }
}

class Altinn2VarselKlientMedFilter(
    private val repository: EksternVarslingRepository,
    private val altinnVarselKlient: Altinn2VarselKlientImpl,
    private val loggingKlient: Altinn2VarselKlientLogging,
) : Altinn2VarselKlient {

    override suspend fun send(eksternVarsel: EksternVarsel): AltinnVarselKlientResponseOrException {
        val mottaker = when (eksternVarsel) {
            is EksternVarsel.Altinntjeneste -> "${eksternVarsel.serviceCode}:${eksternVarsel.serviceEdition}"

            // kun tjeneste varsling som bruker soap klienten
            else -> throw UnsupportedOperationException("Unsupported varseltype: $eksternVarsel")
        }
        return if (repository.mottakerErPåAllowList(mottaker)) {
            altinnVarselKlient.send(eksternVarsel)
        } else {
            loggingKlient.send(eksternVarsel)
        }
    }
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
class Altinn2VarselKlientImpl(
    altinnEndPoint: String = "http://altinn-varsel-firewall.fager/ServiceEngineExternal/NotificationAgencyExternalBasic.svc",
    private val altinnBrukernavn: String = System.getenv("ALTINN_BASIC_WS_BRUKERNAVN") ?: "",
    private val altinnPassord: String = System.getenv("ALTINN_BASIC_WS_PASSORD") ?: "",
    authClient: AuthClient = AuthClientImpl(TexasAuthConfig.nais(), IdentityProvider.AZURE_AD),
) : Altinn2VarselKlient {
    val log = logger()

    private val azureTarget: String = basedOnEnv(
        prod = { "api://prod-gcp.fager.altinn-varsel-firewall/.default" },
        dev = { "api://dev-gcp.fager.altinn-varsel-firewall/.default" },
        other = { " " }
    )

    private val wsclient = createServicePort(altinnEndPoint, INotificationAgencyExternalBasic::class.java) {
        authClient.token(azureTarget).fold(
            onSuccess = { it.accessToken },
            onError = { throw Exception("Failed to fetch token: ${it.error}") }
        )
    }

    override suspend fun send(eksternVarsel: EksternVarsel) = when (eksternVarsel) {
        is EksternVarsel.Altinntjeneste -> sendAltinntjeneste(
            virksomhetsnummer = eksternVarsel.fnrEllerOrgnr,
            serviceCode = eksternVarsel.serviceCode,
            serviceEdition = eksternVarsel.serviceEdition,
            tittel = eksternVarsel.tittel,
            innhold = eksternVarsel.innhold,
        )

        is EksternVarsel.Epost,
        is EksternVarsel.Sms,
        is EksternVarsel.Altinnressurs -> throw UnsupportedOperationException("Unsupported varseltype: $eksternVarsel")
    }

    suspend fun sendSms(
        mobilnummer: String,
        reporteeNumber: String,
        tekst: String,
    ) = send(StandaloneNotificationBEList().apply {
        standaloneNotification.apply {
            add(StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")

                this.reporteeNumber = ns("ReporteeNumber", reporteeNumber)
                receiverEndPoints = ns("ReceiverEndPoints",
                    ReceiverEndPointBEList().apply {
                        receiverEndPoint.apply {
                            add(
                                ReceiverEndPoint().apply {
                                    transportType = ns("TransportType", TransportType.SMS)
                                    receiverAddress = ns("ReceiverAddress", mobilnummer)
                                }
                            )
                        }
                    }
                )

                textTokens = ns("TextTokens",
                    TextTokenSubstitutionBEList().apply {
                        textToken.apply {
                            add(
                                TextToken().apply {
                                    tokenValue = ns("TokenValue", tekst)
                                }
                            )
                            add(
                                TextToken().apply {
                                    tokenValue = ns("TokenValue", "")
                                }
                            )
                        }
                    }
                )
                useServiceOwnerShortNameAsSenderOfSms = ns("UseServiceOwnerShortNameAsSenderOfSms", true)
            })
        }
    })

    suspend fun sendEpost(
        reporteeNumber: String,
        epostadresse: String,
        tittel: String,
        tekst: String,
    ) = send(StandaloneNotificationBEList().apply {
        standaloneNotification.apply {
            add(StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")

                this.reporteeNumber = ns("ReporteeNumber", reporteeNumber)
                receiverEndPoints = ns("ReceiverEndPoints",
                    ReceiverEndPointBEList().apply {
                        receiverEndPoint.apply {
                            add(
                                ReceiverEndPoint().apply {
                                    transportType = ns("TransportType", TransportType.EMAIL)
                                    receiverAddress = ns("ReceiverAddress", epostadresse)
                                }
                            )
                        }
                    }
                )

                textTokens = ns("TextTokens",
                    TextTokenSubstitutionBEList().apply {
                        textToken.apply {
                            add(
                                TextToken().apply {
                                    tokenValue = ns("TokenValue", tittel)
                                }
                            )
                            add(
                                TextToken().apply {
                                    tokenValue = ns("TokenValue", tekst)
                                }
                            )
                        }
                    }
                )
                fromAddress = ns("FromAddress", "ikke-svar@nav.no")
            })
        }
    })

    private suspend fun sendAltinntjeneste(
        serviceCode: String,
        serviceEdition: String,
        virksomhetsnummer: String,
        tittel: String,
        innhold: String,
    ) = send(StandaloneNotificationBEList().apply {
        standaloneNotification.apply {
            add(StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")
                reporteeNumber = ns("ReporteeNumber", virksomhetsnummer)
                service = ns("Service", Service().apply {
                    this.serviceCode = serviceCode
                    this.serviceEdition = serviceEdition.toInt()
                })

                receiverEndPoints = ns("ReceiverEndPoints",
                    ReceiverEndPointBEList().apply {
                        receiverEndPoint.apply {
                            add(
                                ReceiverEndPoint().apply {
                                    transportType = ns("TransportType", TransportType.EMAIL_PREFERRED)
                                }
                            )
                        }
                    }
                )

                textTokens = ns("TextTokens",
                    TextTokenSubstitutionBEList().apply {
                        textToken.apply {
                            add(
                                TextToken().apply {
                                    tokenValue = ns("TokenValue", tittel)
                                }
                            )
                            add(
                                TextToken().apply {
                                    tokenValue = ns("TokenValue", innhold)
                                }
                            )
                        }
                    }
                )
                fromAddress = ns("FromAddress", "ikke-svar@nav.no")
            })
        }
    })

    private suspend fun send(payload: StandaloneNotificationBEList): AltinnVarselKlientResponseOrException {
        return blockingIO {
            try {
                val response = withRetryHandler(
                    maxAttempts = 3,
                    delay = 1000.milliseconds,
                    isRetryable = { it.erDriftsforstyrrelse() }
                ) {
                    wsclient.sendStandaloneNotificationBasicV3(
                        altinnBrukernavn,
                        altinnPassord,
                        payload
                    )
                }
                AltinnVarselKlientResponse.Ok(
                    rå = try {
                        laxObjectMapper.valueToTree<JsonNode>(response)
                    } catch (e: RuntimeException) {
                        e.rethrowIfCancellation()
                        log.error(e)
                        TextNode(e.message)
                    },
                )
            } catch (e: INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage) {
                AltinnVarselKlientResponse.Feil(
                    rå = laxObjectMapper.valueToTree(e),
                    altinnFault = e.faultInfo,
                )
            } catch (e: RuntimeException) {
                e.rethrowIfCancellation()
                UkjentException(e)
            }
        }
    }

}

/**
 * er driftsforstyrrelse hvis http 502,503 eller WstxEOFEx, WebServiceException
 */
fun Exception.erDriftsforstyrrelse(): Boolean {
    val isEofEx = isCausedBy<com.ctc.wstx.exc.WstxEOFException>()
    val isConnectEx = isCausedBy<java.net.ConnectException>()
    val is50234Ex = findCause<HTTPException>()?.let {
        listOf(502, 503).contains(it.responseCode)
    } ?: false
    val isWebServiceEx = isCausedBy<jakarta.xml.ws.WebServiceException>()

    return isEofEx || isConnectEx || is50234Ex || isWebServiceEx
}


@Suppress("HttpUrlsUsage")
inline fun <reified T> ns(localpart: String, value: T): JAXBElement<T> {
    val ns = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10"
    return JAXBElement(QName(ns, localpart), T::class.java, value)
}

fun <PORT_TYPE> createServicePort(
    url: String,
    clazz: Class<PORT_TYPE>,
    createAuthorizeToken: suspend () -> String,
): PORT_TYPE = JaxWsProxyFactoryBean().apply {
    address = url
    serviceClass = clazz
    /* mask credentials */
    inInterceptors.add(LoggingInInterceptor().apply {
        addSensitiveProtocolHeaderNames(setOf("Authorization"))
        addSensitiveElementNames(setOf("systemUserName", "systemPassword"))
    })
    outInterceptors.add(LoggingOutInterceptor().apply {
        addSensitiveProtocolHeaderNames(setOf("Authorization"))
        addSensitiveElementNames(setOf("systemUserName", "systemPassword"))
    })

    /* inject Azure AD token */
    outInterceptors.add(object : AbstractPhaseInterceptor<Message>(Phase.PRE_STREAM) {
        override fun handleMessage(message: Message?) {
            if (message == null || message[Message.INBOUND_MESSAGE] as? Boolean != false) {
                return
            }

            @Suppress("UNCHECKED_CAST")
            val headers = message[Message.PROTOCOL_HEADERS] as MutableMap<String, MutableList<String>>?
                ?: mutableMapOf()

            val token = runBlocking { createAuthorizeToken() }

            val authorizationHeaders = headers.computeIfAbsent(Authorization) {
                mutableListOf()
            }

            authorizationHeaders.add("Bearer $token")
            message[Message.PROTOCOL_HEADERS] = headers
        }
    })
}.create(clazz)