package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnPlattformTokenClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnPlattformTokenClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.defaultHttpClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import kotlin.time.Duration.Companion.milliseconds


/**
 * Klient for å sende varsler til Altinn 3
 * https://docs.altinn.studio/notifications/reference/api/openapi/#/Orders/post_orders
 */
class Altinn3VarselKlientImpl(
    val altinnBaseUrl: String = System.getenv("ALTINN_3_API_BASE_URL"),
    val altinnPlattformTokenClient: AltinnPlattformTokenClient = AltinnPlattformTokenClientImpl(),
    val httpClient: HttpClient = defaultHttpClient(),
) : Altinn3VarselKlient {

    override suspend fun send(eksternVarsel: EksternVarsel) =
        when (val orderResponse = order(eksternVarsel)) {
            is OrderResponse.Success -> {
                delay(500.milliseconds)
                notifications(orderResponse.orderId)
            }

            is ErrorResponse -> orderResponse
        }

    suspend fun order(eksternVarsel: EksternVarsel) = try {
        httpClient.post {
            url("$altinnBaseUrl/notifications/api/v1/orders")
            plattformTokenBearerAuth()
            contentType(ContentType.Application.Json)
            setBody(OrderRequest.from(eksternVarsel))
        }.body<OrderResponse.Success>()
    } catch (e: ResponseException) {
        ErrorResponse(
            message = e.response.status.description,
            code = e.response.status.value.toString()
        )
    } catch (e: Exception) {
        ErrorResponse(
            message = e.message ?: "",
            code = e::class.java.simpleName ?: ""
        )
    }

    suspend fun notifications(orderId: String): NotificationsResponse = try {
        val sms = smsNotifications(orderId)
        val email = emailNotifications(orderId)
        sms + email
    } catch (e: ResponseException) {
        ErrorResponse(
            message = e.response.status.description,
            code = e.response.status.value.toString()
        )
    } catch (e: Exception) {
        ErrorResponse(
            message = e.message ?: "",
            code = e::class.java.simpleName ?: ""
        )
    }

    private suspend fun emailNotifications(orderId: String): NotificationsResponse.Success = httpClient.get {
        url("$altinnBaseUrl/notifications/api/v1/orders/$orderId/notifications/email")
        plattformTokenBearerAuth()
    }.body()

    private suspend fun smsNotifications(orderId: String): NotificationsResponse.Success = httpClient.get {
        url("$altinnBaseUrl/notifications/api/v1/orders/$orderId/notifications/sms")
        plattformTokenBearerAuth()
    }.body()

    private suspend fun HttpMessageBuilder.plattformTokenBearerAuth() {
        altinnPlattformTokenClient.token("altinn:serviceowner/notifications.create").let {
            headers {
                bearerAuth(it)
            }
        }
    }
}


interface Altinn3VarselKlient {
    //suspend fun order(eksternVarsel: EksternVarsel): OrderResponse
    //suspend fun notifications(orderId: String): NotificationsResponse
    suspend fun send(eksternVarsel: EksternVarsel): NotificationsResponse


    /**
     * DTOs
     */

    @Suppress("unused")
    sealed class OrderRequest {
        data class Email(
            @JsonIgnore val reciever: String,
            @JsonIgnore val subject: String,
            @JsonIgnore val body: String,
        ) : OrderRequest() {
            val notificationChannel
                get() = "Email"
            val recipients
                get() = listOf(
                    mapOf("emailAddress" to reciever)
                )
            val emailTemplate
                get() = mapOf(
                    "subject" to subject,
                    "body" to body,
                    "contentType" to "Html",
                )
        }

        data class Sms(
            @JsonIgnore val reciever: String,
            @JsonIgnore val body: String,
        ) : OrderRequest() {
            val notificationChannel
                get() = "Sms"
            val recipients
                get() = listOf(
                    mapOf("mobileNumber" to reciever)
                )
            val smsTemplate
                get() = mapOf(
                    "body" to body
                )
        }

        data class Resource(
            val resourceId: String,
            @JsonIgnore val orgnr: String,
            @JsonIgnore val epostTittel: String,
            @JsonIgnore val epostInnhold: String,
            @JsonIgnore val smsInnhold: String,
        ) : OrderRequest() {
            val notificationChannel
                get() = "EmailPreferred"
            val recipients
                get() = listOf(
                    mapOf("organizationNumber" to orgnr)
                )
            val emailTemplate
                get() = mapOf(
                    "subject" to epostTittel,
                    "body" to epostInnhold,
                    "contentType" to "Html",
                )
            val smsTemplate
                get() = mapOf(
                    "body" to smsInnhold
                )
        }

        companion object {
            fun from(eksternVarsel: EksternVarsel) = when (eksternVarsel) {
                is EksternVarsel.Altinnressurs -> Resource(
                    orgnr = eksternVarsel.fnrEllerOrgnr,
                    resourceId = eksternVarsel.resourceId,
                    epostTittel = eksternVarsel.epostTittel,
                    epostInnhold = eksternVarsel.epostInnhold,
                    smsInnhold = eksternVarsel.smsInnhold,
                )

                is EksternVarsel.Epost -> Email(
                    reciever = eksternVarsel.epostadresse,
                    subject = eksternVarsel.tittel,
                    body = eksternVarsel.body,
                )

                is EksternVarsel.Sms -> Sms(
                    reciever = eksternVarsel.mobilnummer,
                    body = eksternVarsel.tekst,
                )

                is EksternVarsel.Altinntjeneste -> throw UnsupportedOperationException("Altinntjeneste er ikke støttet")
            }
        }
    }

    data class ErrorResponse(
        val message: String,
        val code: String,
    ) : OrderResponse, NotificationsResponse

    sealed interface OrderResponse {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Success(
            val orderId: String,
        ) : OrderResponse
    }

    @Suppress("unused")
    sealed interface NotificationsResponse {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Success(
            val orderId: String,
            val sendersReference: String? = null,
            val generated: Int,
            val succeeded: Int,
            val notifications: List<Notification>,
        ) : NotificationsResponse {
            operator fun plus(other: Success) = Success(
                orderId = orderId,
                sendersReference = sendersReference ?: other.sendersReference,
                generated = generated + other.generated,
                succeeded = succeeded + other.succeeded,
                notifications = notifications + other.notifications,
            )

            data class Notification(
                val id: String,
                val succeeded: Boolean,
                val recipient: Recipient,
                val sendStatus: SendStatus,
            ) {
                @JsonInclude(JsonInclude.Include.NON_NULL)
                data class Recipient(
                    val emailAddress: String? = null,
                    val mobileNumber: String? = null,
                    val organizationNumber: String? = null,
                    val nationalIdentityNumber: String? = null,
                )

                data class SendStatus(
                    /**
                     * status should be enum but is string in api. prevent breakage by using string
                     * https://docs.altinn.studio/notifications/reference/api/endpoints/get-email-notifications/
                     *
                     * enum class Status {
                     *     New, // The email has been created but has not yet been picked up for processing.
                     *     Sending, //	The email is being processed and will be sent shortly.
                     *     Succeeded, // The email has been accepted by the third-party service and will be sent soon.
                     *     Delivered, // The email was successfully delivered to the recipient. No errors were reported, indicating successful delivery.
                     *     Failed, // The email was not sent due to an unspecified failure.
                     *     Failed_RecipientNotIdentified, // The email was not sent because the recipient’s email address could not be found.
                     *     Failed_InvalidEmailFormat, // The email was not sent due to an invalid email address format.
                     *     Failed_Bounced, // The email bounced due to issues like a non-existent email address or invalid domain.
                     *     Failed_FilteredSpam, //	The email was identified as spam and rejected or blocked (not quarantined).
                     *     Failed_Quarantined, // The email was quarantined due to being flagged as spam, bulk mail, or phishing.
                     * }
                     */

                    val status: String,
                    val description: String,
                    val lastUpdate: String,
                )


            }
        }
    }
}


class Altinn3VarselKlientMedFilter(
    private val repository: EksternVarslingRepository,
    private val varselKlient: Altinn3VarselKlient,
    private val loggingKlient: Altinn3VarselKlientLogging
) : Altinn3VarselKlient {

    override suspend fun send(eksternVarsel: EksternVarsel): NotificationsResponse {
        val mottaker = when (eksternVarsel) {
            is EksternVarsel.Sms -> eksternVarsel.mobilnummer
            is EksternVarsel.Epost -> eksternVarsel.epostadresse
            is EksternVarsel.Altinnressurs -> eksternVarsel.resourceId
            is EksternVarsel.Altinntjeneste -> throw UnsupportedOperationException("Altinntjeneste er ikke støttet")
        }
        return if (repository.mottakerErPåAllowList(mottaker)) {
            varselKlient.send(eksternVarsel)
        } else {
            loggingKlient.send(eksternVarsel)
        }
    }
}

class Altinn3VarselKlientLogging : Altinn3VarselKlient {
    private val log = logger()

    override suspend fun send(eksternVarsel: EksternVarsel): NotificationsResponse {
        log.info("send($eksternVarsel)")
        return NotificationsResponse.Success(
            orderId = "",
            sendersReference = null,
            generated = 0,
            succeeded = 0,
            notifications = listOf()
        )
    }
}