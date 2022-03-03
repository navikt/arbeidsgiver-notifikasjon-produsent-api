package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import io.ktor.http.HttpHeaders.Authorization
import kotlinx.coroutines.runBlocking
import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType
import no.altinn.schemas.services.serviceengine.notification._2009._10.*
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2015._06.Service
import no.altinn.services.common.fault._2009._10.AltinnFault
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasic
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.blockingIO
import no.nav.tms.token.support.azure.exchange.AzureService
import no.nav.tms.token.support.azure.exchange.AzureServiceBuilder
import org.apache.cxf.ext.logging.LoggingInInterceptor
import org.apache.cxf.ext.logging.LoggingOutInterceptor
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.message.Message
import org.apache.cxf.phase.AbstractPhaseInterceptor
import org.apache.cxf.phase.Phase
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
}

class AltinnVarselKlientLogging : AltinnVarselKlient {
    private val log = logger()

    override suspend fun send(eksternVarsel: EksternVarsel): Result<AltinnVarselKlient.AltinnResponse> {
        log.info("send($eksternVarsel)")
        return Result.success(
            AltinnVarselKlient.AltinnResponse.Ok(
                rå = NullNode.instance
            )
        )
    }
}

class AltinnVarselKlientMedFilter(
    private val repository: EksternVarslingRepository,
    private val altinnVarselKlient: AltinnVarselKlientImpl,
    private val loggingKlient: AltinnVarselKlientLogging,
) : AltinnVarselKlient {

    override suspend fun send(eksternVarsel: EksternVarsel): Result<AltinnVarselKlient.AltinnResponse> {
        val mottaker = when(eksternVarsel) {
            is EksternVarsel.Sms -> eksternVarsel.mobilnummer
            is EksternVarsel.Epost -> eksternVarsel.epostadresse
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
class AltinnVarselKlientImpl(
    altinnEndPoint: String = "http://altinn-varsel-firewall.fager/ServiceEngineExternal/NotificationAgencyExternalBasic.svc",
    private val altinnBrukernavn: String = System.getenv("ALTINN_BASIC_WS_BRUKERNAVN") ?: "",
    private val altinnPassord: String = System.getenv("ALTINN_BASIC_WS_PASSORD") ?: "",
    azureService: AzureService = AzureServiceBuilder.buildAzureService(),
    azureTargetApp: String = basedOnEnv(
        prod = { "prod-gcp.fager.altinn-varsel-firewall" },
        dev = { "dev-gcp.fager.altinn-varsel-firewall" },
        other = { " "}
    ),
): AltinnVarselKlient {
    val log = logger()
    private val wsclient = createServicePort(altinnEndPoint, INotificationAgencyExternalBasic::class.java) {
        azureService.getAccessToken(azureTargetApp)
    }

    override suspend fun send(eksternVarsel: EksternVarsel): Result<AltinnVarselKlient.AltinnResponse> {
        return when (eksternVarsel) {
            is EksternVarsel.Epost -> sendEpost(
                reporteeNumber = eksternVarsel.fnrEllerOrgnr,
                epostadresse = eksternVarsel.epostadresse,
                tittel = eksternVarsel.tittel,
                tekst = eksternVarsel.body,
            )
            is EksternVarsel.Sms -> sendSms(
                reporteeNumber = eksternVarsel.fnrEllerOrgnr,
                mobilnummer = eksternVarsel.mobilnummer,
                tekst = eksternVarsel.tekst,
            )
        }
    }

    suspend fun sendSms(
        mottaker: AltinnMottaker,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")
                reporteeNumber = ns("ReporteeNumber", mottaker.virksomhetsnummer)
                service = ns("Service", Service().apply {
                    serviceCode = mottaker.serviceCode
                    serviceEdition = mottaker.serviceEdition.toInt()
                })

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

    suspend fun sendSms(
        mobilnummer: String,
        reporteeNumber: String,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")

                this.reporteeNumber = ns("ReporteeNumber", reporteeNumber)
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

    suspend fun sendEpost(
        mottaker: AltinnMottaker,
        tittel: String,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")
                reporteeNumber = ns("ReporteeNumber", mottaker.virksomhetsnummer)
                service = ns("Service", Service().apply {
                    serviceCode = mottaker.serviceCode
                    serviceEdition = mottaker.serviceEdition.toInt()
                })

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

    suspend fun sendEpost(
        reporteeNumber: String,
        epostadresse: String,
        tittel: String,
        tekst: String,
    ): Result<AltinnVarselKlient.AltinnResponse> {
        return send(StandaloneNotificationBEList().withStandaloneNotification(
            StandaloneNotification().apply {
                languageID = 1044
                notificationType = ns("NotificationType", "TokenTextOnly")

                this.reporteeNumber = ns("ReporteeNumber", reporteeNumber)
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
                        rå = laxObjectMapper.valueToTree(response),
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
                        rå = laxObjectMapper.valueToTree(e),
                    )
                )
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
    }
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
        addSensitiveElementNames(setOf("systemUserName", "systemPassword", "ns2:ReporteeNumber"))
    })
    outInterceptors.add(LoggingOutInterceptor().apply {
        addSensitiveProtocolHeaderNames(setOf("Authorization"))
        addSensitiveElementNames(setOf("systemUserName", "systemPassword", "ns2:ReporteeNumber"))
    })

    /* inject Azure AD token */
    outInterceptors.add(object: AbstractPhaseInterceptor<Message>(Phase.PRE_STREAM) {
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