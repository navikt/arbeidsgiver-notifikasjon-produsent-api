package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.convertValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnPlattformTokenClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnPlattformTokenClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.defaultHttpClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ensurePrefix
import java.time.OffsetDateTime
import java.util.*

/**
 * Klient for å sende varsler til Altinn 3
 * https://docs.altinn.studio/notifications/reference/api/openapi/#/Orders/post_orders
 *
 * Her skal det komme et bedre API etterhvert. Apiet skal støtte idempotens og enklere kunne hente ut alle varsler.
 * ref: https://altinn.slack.com/archives/C069J71UQCQ/p1740582966140809?thread_ts=1740580333.657779&cid=C069J71UQCQ
 * https://github.com/Altinn/altinn-notifications/tree/feature/api-v2/src/Altinn.Notifications.NewApiDemo
 */
open class Altinn3VarselKlientImpl(
    val altinnBaseUrl: String = System.getenv("ALTINN_3_API_BASE_URL"),
    val altinnPlattformTokenClient: AltinnPlattformTokenClient = AltinnPlattformTokenClientImpl(),
    val httpClient: HttpClient = defaultHttpClient(),
) : Altinn3VarselKlient {

    private val log = logger()

    override suspend fun order(eksternVarsel: EksternVarsel) = try {
        httpClient.post {
            url("$altinnBaseUrl/notifications/api/v1/orders")
            plattformTokenBearerAuth()
            contentType(ContentType.Application.Json)
            setBody(OrderRequest.from(eksternVarsel))
        }.body<JsonNode>().let {
            OrderResponse.Success.fromJson(it)
        }
    } catch (e: ResponseException) {
        ErrorResponse(
            message = """${e.response.status.description}: ${e.response.bodyAsText()}""",
            code = e.response.status.value.toString(),
            rå = e.response.body()
        )
    } catch (e: Exception) {
        log.error("Unexpected error", e)
        ErrorResponse(
            message = e.message ?: "",
            code = e::class.java.simpleName ?: "",
            rå = TextNode.valueOf(e.toString())
        )
    }

    override suspend fun notifications(orderId: String): NotificationsResponse = try {
        val sms = smsNotifications(orderId)
        val email = emailNotifications(orderId)
        sms + email
    } catch (e: ResponseException) {
        ErrorResponse(
            message = e.response.status.description,
            code = e.response.status.value.toString(),
            rå = e.response.body()
        )
    } catch (e: Exception) {
        log.error("Unexpected error", e)
        ErrorResponse(
            message = e.message ?: "",
            code = e::class.java.simpleName ?: "",
            rå = TextNode.valueOf(e.toString())
        )
    }

    override suspend fun orderStatus(orderId: String): OrderStatusResponse = try {
        httpClient.get {
            url("$altinnBaseUrl/notifications/api/v1/orders/$orderId/status")
            plattformTokenBearerAuth()
        }.body<JsonNode>().let {
            OrderStatusResponse.Success.fromJson(it)
        }
    } catch (e: ResponseException) {
        ErrorResponse(
            message = e.response.status.description,
            code = e.response.status.value.toString(),
            rå = e.response.body()
        )
    } catch (e: Exception) {
        ErrorResponse(
            message = e.message ?: "",
            code = e::class.java.simpleName ?: "",
            rå = TextNode.valueOf(e.toString())
        )
    }

    private suspend fun emailNotifications(orderId: String): NotificationsResponse.Success = httpClient.get {
        url("$altinnBaseUrl/notifications/api/v1/orders/$orderId/notifications/email")
        plattformTokenBearerAuth()
    }.body<JsonNode>().let {
        NotificationsResponse.Success.fromJson(it)
    }

    private suspend fun smsNotifications(orderId: String): NotificationsResponse.Success = httpClient.get {
        url("$altinnBaseUrl/notifications/api/v1/orders/$orderId/notifications/sms")
        plattformTokenBearerAuth()
    }.body<JsonNode>().let {
        NotificationsResponse.Success.fromJson(it)
    }

    private suspend fun HttpMessageBuilder.plattformTokenBearerAuth() {
        altinnPlattformTokenClient.token("altinn:serviceowner/notifications.create").let {
            headers {
                bearerAuth(it)
            }
        }
    }
}


interface Altinn3VarselKlient {
    suspend fun order(eksternVarsel: EksternVarsel): OrderResponse
    suspend fun notifications(orderId: String): NotificationsResponse
    suspend fun orderStatus(orderId: String): OrderStatusResponse


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
                    reciever = fiksGyldigMobilnummer(eksternVarsel.mobilnummer),
                    body = eksternVarsel.tekst,
                )

                is EksternVarsel.Altinntjeneste -> throw UnsupportedOperationException("Altinntjeneste er ikke støttet")
            }

            /**
             * På grunn av en endring i altinn 3 apiet i forhold til soap api, så støttes ikke lenger mobilnummer uten
             * landkode: https://docs.altinn.studio/nb/notifications/what-do-you-get/#supported-recipient-numbers
             *
             * I produsent-api tillatter vi kun norske mobilnummer, men krever ikke landkode. Vi antar norge på samme måte
             * som det gamle altinn apiet.
             * Dersom vi åpner opp for andre land, må vi også endre valideringen i graphql apiet slik at landkode må angis.
             * Dersom dette gjøres kan denne mapping koden fjernes i sin helhet.
             */
            private fun fiksGyldigMobilnummer(mobilnummer: String): String {
                return when {
                    mobilnummer.startsWith("0047") -> {
                        mobilnummer
                    }

                    mobilnummer.startsWith("+47") -> {
                        mobilnummer
                    }

                    else -> {
                        mobilnummer.ensurePrefix("+47")
                    }
                }
            }
        }
    }

    data class ErrorResponse(
        override val rå: JsonNode,
        val message: String,
        val code: String,
    ) : OrderResponse, NotificationsResponse, OrderStatusResponse {
        fun isRetryable() =
            when (code) {
                "400" -> false

                else -> true
            }

        override fun toString(): String {
            return "ErrorResponse(message='$message', code='$code')"
        }
    }

    sealed interface OrderResponse {
        val rå: JsonNode

        data class Success(
            override val rå: JsonNode,
            val orderId: String,
        ) : OrderResponse {

            companion object {
                fun fromJson(rawJson: JsonNode): Success {
                    return Success(
                        rawJson,
                        orderId = rawJson["orderId"].asText()
                    )
                }
            }
        }
    }

    @Suppress("unused")
    sealed interface OrderStatusResponse {
        val rå: JsonNode

        enum class ProcessingStatusValue {
            Registered,
            Processing,
            Completed,
            Cancelled
        }

        data class ProcessingStatus(
            /**
             * status should be enum but is string in api. prevent breakage by using string
             * https://docs.altinn.studio/notifications/reference/api/endpoints/get-email-notifications/
             *
             * enum class ProcessingStatusValue {
             *     Registered,
             *     Processing,
             *     Processed,
             *     Completed,
             *     Cancelled
             * }
             *
             */
            val status: String,
            val description: String?,
            val lastUpdate: OffsetDateTime?,
        ) {
            companion object {
                const val Processing = "Processing"
                const val Processed = "Processed"
                const val Registered = "Registered"
                const val Completed = "Completed"
                const val Cancelled = "Cancelled"
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class NotificationStatusSummary(
            val generated: Int,
            val succeeded: Int,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Success(
            override val rå: JsonNode,
            val orderId: String,
            val sendersReference: String?,
            val processingStatus: ProcessingStatus,
            val notificationsStatusSummary: NotificationStatusSummary?,
        ) : OrderStatusResponse {
            companion object {
                fun fromJson(rawJson: JsonNode): Success {
                    return Success(
                        rå = rawJson,
                        orderId = rawJson["id"].asText(),
                        sendersReference = rawJson["sendersReference"]?.asText(null),
                        processingStatus = laxObjectMapper.convertValue(rawJson["processingStatus"]),
                        notificationsStatusSummary = laxObjectMapper.convertValue(
                            rawJson["notificationsStatusSummary"] ?: NullNode.instance
                        )
                    )
                }
            }
        }
    }


    @Suppress("unused")
    sealed interface NotificationsResponse {
        val rå: JsonNode

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Success(
            override val rå: JsonNode,
            val orderId: String,
            val sendersReference: String? = null,
            val generated: Int,
            val succeeded: Int,
            val notifications: List<Notification>,
        ) : NotificationsResponse {

            val isProcessing
                get() = notifications.any { it.sendStatus.isProcessing }

            companion object {
                fun fromJson(rawJson: JsonNode): Success {
                    return Success(
                        rå = rawJson,
                        orderId = rawJson["orderId"].asText(),
                        sendersReference = rawJson["sendersReference"]?.asText(null),
                        generated = rawJson["generated"].asInt(),
                        succeeded = rawJson["succeeded"].asInt(),
                        notifications = laxObjectMapper.convertValue(rawJson["notifications"])
                    )
                }
            }

            operator fun plus(other: Success) = Success(
                orderId = orderId,
                sendersReference = sendersReference ?: other.sendersReference,
                generated = generated + other.generated,
                succeeded = succeeded + other.succeeded,
                notifications = notifications + other.notifications,
                rå = JsonNodeFactory.instance.arrayNode().apply {
                    add(rå)
                    add(other.rå)
                }
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
                     * https://docs.altinn.studio/notifications/reference/api/endpoints/get-sms-notifications/
                     *
                     * enum class Status {
                     *     New,         // The email has been created but has not yet been picked up for processing.
                     *     New,         // The SMS has been created but has not yet been picked up for processing.
                     *
                     *     Sending,     // The email is being processed and will be sent shortly.
                     *     Sending,     // The SMS is being processed and will be sent shortly.
                     *
                     *     Accepted,    // The SMS has been accepted by the gateway service and will be sent soon.
                     *
                     *     Succeeded,   // The email has been accepted by the third-party service and will be sent soon.
                     *
                     *     Delivered,   // The email was successfully delivered to the recipient. No errors were reported, indicating successful delivery.
                     *     Delivered,   // The SMS was successfully delivered to the recipient.
                     *
                     *     Failed,      // The email was not sent due to an unspecified failure.
                     *     Failed,       // The SMS was not sent due to an unspecified failure.
                     *
                     *     Failed_RecipientNotIdentified,   // The email was not sent because the recipient’s email address could not be found.
                     *     Failed_RecipientNotIdentified,   // The SMS was not sent because the recipient’s SMS address was not found.

                     *     Failed_InvalidEmailFormat,       // The email was not sent due to an invalid email address format.
                     *     Failed_InvalidRecipient,         // The SMS was not sent because the recipient mobile number was invalid.

                     *     Failed_Bounced,                  // The email bounced due to issues like a non-existent email address or invalid domain.
                     *     Failed_BarredReceiver,           // The SMS was not delivered because the recipient’s mobile number is barred, blocked or not in use.

                     *     Failed_FilteredSpam,             // The email was identified as spam and rejected or blocked (not quarantined).
                     *     Failed_Rejected,                 // The SMS was not delivered because it was rejected.

                     *     Failed_Quarantined,              // The email was quarantined due to being flagged as spam, bulk mail, or phishing.
                     *
                     *     Failed_Deleted,                  // The SMS was not delivered because the message has been deleted.
                     *     Failed_Expired,                  // The SMS was not delivered because it has been expired.
                     *     Failed_Undelivered,              // The SMS was not delivered due to invalid mobile number or no available route to destination.
                     * }
                     */

                    val status: String,
                    val description: String,
                    val lastUpdate: String,
                ) {
                    val isProcessing
                        get() = status in listOf(
                            New,
                            Sending,
                            Accepted,
                            Succeeded
                        )

                    companion object {
                        val New = "New"
                        val Sending = "Sending"
                        val Accepted = "Accepted"
                        val Succeeded = "Succeeded"
                        val Delivered = "Delivered"
                        val Failed = "Failed"
                        val Failed_RecipientNotIdentified = "Failed_RecipientNotIdentified"
                        val Failed_InvalidEmailFormat = "Failed_InvalidEmailFormat"
                        val Failed_Bounced = "Failed_Bounced"
                        val Failed_FilteredSpam = "Failed_FilteredSpam"
                        val Failed_Quarantined = "Failed_Quarantined"
                        val Failed_BarredReceiver = "Failed_BarredReceiver"
                        val Failed_Deleted = "Failed_Deleted"
                        val Failed_Expired = "Failed_Expired"
                        val Failed_InvalidRecipient = "Failed_InvalidRecipient"
                        val Failed_Undelivered = "Failed_Undelivered"
                        val Failed_Rejected = "Failed_Rejected"
                    }
                }
            }
        }
    }
}


class Altinn3VarselKlientMedFilter(
    private val repository: EksternVarslingRepository,
    private val loggingKlient: Altinn3VarselKlientLogging
) : Altinn3VarselKlientImpl() {

    override suspend fun order(eksternVarsel: EksternVarsel): OrderResponse {
        val mottaker = when (eksternVarsel) {
            is EksternVarsel.Sms -> eksternVarsel.mobilnummer
            is EksternVarsel.Epost -> eksternVarsel.epostadresse
            is EksternVarsel.Altinnressurs -> eksternVarsel.resourceId
            is EksternVarsel.Altinntjeneste -> throw UnsupportedOperationException("Altinntjeneste er ikke støttet")
        }
        return if (repository.mottakerErPåAllowList(mottaker)) {
            super.order(eksternVarsel)
        } else {
            loggingKlient.order(eksternVarsel)
        }
    }

    override suspend fun notifications(orderId: String): NotificationsResponse {
        return if (repository.ordreHarMottakerPåAllowlist(orderId)){
            super.notifications(orderId)
        } else {
            loggingKlient.notifications(orderId)
        }
    }

    override suspend fun orderStatus(orderId: String): OrderStatusResponse {
        return if (repository.ordreHarMottakerPåAllowlist(orderId)){
            super.orderStatus(orderId)
        } else {
            loggingKlient.orderStatus(orderId)
        }
    }
}

class Altinn3VarselKlientLogging : Altinn3VarselKlient {
    private val log = logger()

    override suspend fun order(eksternVarsel: EksternVarsel): OrderResponse {
        log.info("order($eksternVarsel)")
        return OrderResponse.Success.fromJson(
            JsonNodeFactory.instance.objectNode().apply {
                put("orderId", "fake-${UUID.randomUUID()}")
            }
        )
    }

    override suspend fun notifications(orderId: String): NotificationsResponse {
        log.info("notifications($orderId)")
        return NotificationsResponse.Success(
            orderId = orderId,
            sendersReference = null,
            generated = 0,
            succeeded = 0,
            notifications = listOf(),
            rå = NullNode.instance,
        )
    }

    override suspend fun orderStatus(orderId: String): OrderStatusResponse {
        log.info("orderStatus($orderId)")
        return OrderStatusResponse.Success(
            orderId = orderId,
            sendersReference = null,
            rå = NullNode.instance,
            processingStatus = OrderStatusResponse.ProcessingStatus(
                status = OrderStatusResponse.ProcessingStatus.Completed,
                description = null,
                lastUpdate = null,
            ),
            notificationsStatusSummary = null,
        )
    }
}