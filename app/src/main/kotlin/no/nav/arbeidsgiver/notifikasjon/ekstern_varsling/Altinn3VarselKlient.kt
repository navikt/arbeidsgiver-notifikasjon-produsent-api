package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.rethrowIfCancellation
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ensurePrefix
import java.time.OffsetDateTime
import java.util.*

/**
 * Klient for å sende varsler til Altinn 3 via det nye /future/orders API-et.
 * https://docs.altinn.studio/nb/notifications/reference/openapi/#/Order/post_future_orders
 *
 * Nytt API støtter idempotens via idempotencyId og enklere statushenting via /future/shipment/{id}.
 */
open class Altinn3VarselKlientImpl(
    val altinnBaseUrl: String = System.getenv("ALTINN_3_API_BASE_URL"),
    val altinnPlattformTokenClient: AltinnPlattformTokenClient = AltinnPlattformTokenClientImpl(),
    val httpClient: HttpClient = defaultHttpClient(
        customizeMetrics = {
            clientName = "altinn3-varsel-client"
            canonicalizer = { path -> path.replace(Regex("[0-9a-fA-F-]{36}"), "{id}") }
        }
    ),
) : Altinn3VarselKlient {

    private val log = logger()

    override suspend fun order(eksternVarsel: EksternVarsel, idempotencyId: String) = try {
        httpClient.post {
            url("$altinnBaseUrl/notifications/api/v1/future/orders")
            plattformTokenBearerAuth()
            contentType(ContentType.Application.Json)
            setBody(OrderRequest.from(eksternVarsel, idempotencyId))
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
        e.rethrowIfCancellation()

        log.error("Unexpected error", e)
        ErrorResponse(
            message = e.message ?: "",
            code = e::class.java.simpleName ?: "",
            rå = TextNode.valueOf(e.toString())
        )
    }

    override suspend fun shipment(shipmentId: String): ShipmentResponse = try {
        httpClient.get {
            url("$altinnBaseUrl/notifications/api/v1/future/shipment/$shipmentId")
            plattformTokenBearerAuth()
        }.body<JsonNode>().let {
            ShipmentResponse.Success.fromJson(it)
        }
    } catch (e: ResponseException) {
        ErrorResponse(
            message = """${e.response.status.description}: ${e.response.bodyAsText()}""",
            code = e.response.status.value.toString(),
            rå = e.response.body()
        )
    } catch (e: Exception) {
        e.rethrowIfCancellation()

        log.error("Unexpected error", e)
        ErrorResponse(
            message = e.message ?: "",
            code = e::class.java.simpleName ?: "",
            rå = TextNode.valueOf(e.toString())
        )
    }

    private suspend fun HttpMessageBuilder.plattformTokenBearerAuth() {
        altinnPlattformTokenClient.token("altinn:serviceowner/notifications.create").let {
            headers {
                bearerAuth(it)
            }
        }
    }
}


private const val EPOST_AVSENDER = "ikke-svar@nav.no"

@Suppress("ConstPropertyName")
interface Altinn3VarselKlient {
    suspend fun order(eksternVarsel: EksternVarsel, idempotencyId: String): OrderResponse
    suspend fun shipment(shipmentId: String): ShipmentResponse


    /**
     * DTOs for the new /future/orders API
     */

    @Suppress("unused")
    sealed class OrderRequest {

        /**
         * Epost til spesifikk epostadresse
         */
        data class Email(
            val idempotencyId: String,
            val recipient: Recipient,
        ) : OrderRequest() {
            data class Recipient(
                val recipientEmail: RecipientEmail,
            )

            data class RecipientEmail(
                val emailAddress: String,
                val emailSettings: EmailSettings,
            )

            data class EmailSettings(
                val subject: String,
                val body: String,
                val contentType: String = "Html",
            ) {
                val fromAddress = EPOST_AVSENDER
            }

            companion object {
                fun from(eksternVarsel: EksternVarsel.Epost, idempotencyId: String) = Email(
                    idempotencyId = idempotencyId,
                    recipient = Recipient(
                        recipientEmail = RecipientEmail(
                            emailAddress = eksternVarsel.epostadresse,
                            emailSettings = EmailSettings(
                                subject = eksternVarsel.tittel,
                                body = eksternVarsel.body,
                            )
                        )
                    )
                )
            }
        }

        /**
         * SMS til spesifikt mobilnummer
         */
        data class Sms(
            val idempotencyId: String,
            val recipient: Recipient,
        ) : OrderRequest() {
            data class Recipient(
                val recipientSms: RecipientSms,
            )

            data class RecipientSms(
                val phoneNumber: String,
                val smsSettings: SmsSettings,
            )

            data class SmsSettings(
                val body: String,
            )

            companion object {
                fun from(eksternVarsel: EksternVarsel.Sms, idempotencyId: String) = Sms(
                    idempotencyId = idempotencyId,
                    recipient = Recipient(
                        recipientSms = RecipientSms(
                            phoneNumber = fiksGyldigMobilnummer(eksternVarsel.mobilnummer),
                            smsSettings = SmsSettings(
                                body = eksternVarsel.tekst,
                            )
                        )
                    )
                )
            }
        }

        /**
         * Varsel basert på Altinn-ressurs med oppslag mot organisasjon.
         * Altinn finner selv kontaktinfo basert på orgnummer og ressurs.
         */
        data class Organization(
            val idempotencyId: String,
            val recipient: Recipient,
        ) : OrderRequest() {
            data class Recipient(
                val recipientOrganization: RecipientOrganization,
            )

            data class RecipientOrganization(
                val orgNumber: String,
                val channelSchema: String,
                val resourceId: String,
                val resourceAction: String = "access",
                val emailSettings: EmailSettings,
                val smsSettings: SmsSettings,
            )

            data class EmailSettings(
                val subject: String,
                val body: String,
                val contentType: String = "Html",
            ) {
                val fromAddress = EPOST_AVSENDER
            }

            data class SmsSettings(
                val body: String,
            )

            companion object {
                fun from(eksternVarsel: EksternVarsel.Altinnressurs, idempotencyId: String) = Organization(
                    idempotencyId = idempotencyId,
                    recipient = Recipient(
                        recipientOrganization = RecipientOrganization(
                            orgNumber = eksternVarsel.fnrEllerOrgnr,
                            channelSchema = "EmailPreferred",
                            resourceId = eksternVarsel.resourceId,
                            emailSettings = EmailSettings(
                                subject = eksternVarsel.epostTittel,
                                body = eksternVarsel.epostInnhold,
                            ),
                            smsSettings = SmsSettings(
                                body = eksternVarsel.smsInnhold,
                            )
                        )
                    )
                )
            }
        }

        companion object {
            fun from(eksternVarsel: EksternVarsel, idempotencyId: String): OrderRequest = when (eksternVarsel) {
                is EksternVarsel.Altinnressurs -> Organization.from(eksternVarsel, idempotencyId)
                is EksternVarsel.Epost -> Email.from(eksternVarsel, idempotencyId)
                is EksternVarsel.Sms -> Sms.from(eksternVarsel, idempotencyId)
                is EksternVarsel.Altinntjeneste -> throw UnsupportedOperationException("Altinntjeneste er ikke støttet")
            }

            /**
             * På grunn av en endring i altinn 3 apiet i forhold til soap api, så støttes ikke lenger mobilnummer uten
             * landkode: https://docs.altinn.studio/nb/notifications/what-do-you-get/#supported-recipient-numbers
             *
             * I produsent-api tillater vi kun norske mobilnummer, men krever ikke landkode. Vi antar norge på samme måte
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
    ) : OrderResponse, ShipmentResponse {
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
            val shipmentId: String,
        ) : OrderResponse {

            companion object {
                fun fromJson(rawJson: JsonNode): Success {
                    return Success(
                        rawJson,
                        orderId = rawJson["notificationOrderId"].asText(),
                        shipmentId = rawJson["notification"]["shipmentId"].asText(),
                    )
                }
            }
        }
    }

    /**
     * Response fra GET /future/shipment/{id}
     * Erstatter de gamle orderStatus og notifications endepunktene.
     */
    sealed interface ShipmentResponse {
        val rå: JsonNode

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Success(
            override val rå: JsonNode,
            val shipmentId: String,
            val sendersReference: String?,
            val type: String?,
            val status: String,
            val lastUpdate: OffsetDateTime?,
            val recipients: List<RecipientDelivery>,
        ) : ShipmentResponse {

            val isOrderProcessing
                get() = status in listOf(
                    ShipmentStatus.Order_Registered,
                    ShipmentStatus.Order_Processing,
                )

            val isOrderCompleted
                get() = status == ShipmentStatus.Order_Completed

            val isOrderProcessed
                get() = status == ShipmentStatus.Order_Processed

            val isOrderCancelled
                get() = status == ShipmentStatus.Order_Cancelled

            val isOrderConditionNotMet
                get() = status == ShipmentStatus.Order_SendConditionNotMet

            /**
             * Sjekker om alle mottakere er ferdige (levert eller feilet)
             */
            val allRecipientsFinished
                get() = recipients.all { it.isTerminal }

            val allRecipientsDelivered
                get() = recipients.all { it.isDelivered }

            val hasFailedRecipients
                get() = recipients.any { it.isFailed }

            companion object {
                fun fromJson(rawJson: JsonNode): Success {
                    return Success(
                        rå = rawJson,
                        shipmentId = rawJson["shipmentId"].asText(),
                        sendersReference = rawJson["sendersReference"]?.asText(null),
                        type = rawJson["type"]?.asText(null),
                        status = rawJson["status"].asText(),
                        lastUpdate = rawJson["lastUpdate"]?.asText(null)?.let { OffsetDateTime.parse(it) },
                        recipients = rawJson["recipients"]?.let { laxObjectMapper.convertValue(it) } ?: emptyList(),
                    )
                }
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class RecipientDelivery(
                val status: String,
                val lastUpdate: String?,
                val destination: String?,
            ) {
                val isDelivered
                    get() = status in listOf(
                        ShipmentStatus.SMS_Delivered,
                        ShipmentStatus.Email_Delivered,
                    )

                val isFailed
                    get() = status.contains("Failed")

                val isProcessing
                    get() = status in listOf(
                        ShipmentStatus.SMS_New,
                        ShipmentStatus.SMS_Sending,
                        ShipmentStatus.SMS_Accepted,
                        ShipmentStatus.Email_New,
                        ShipmentStatus.Email_Sending,
                        ShipmentStatus.Email_Succeeded,
                    )

                val isTerminal
                    get() = isDelivered || isFailed
            }
        }
    }

    /**
     * Alle mulige statusverdier i det nye API-et.
     * Brukes for både order-level og recipient-level statuser.
     *
     * @see <a href="https://docs.altinn.studio/nb/notifications/reference/notification-status/">Altinn Notification Status Reference</a>
     */
    @Suppress("unused")
    object ShipmentStatus {
        // ── Order-level statuser ──────────────────────────────────────────

        /** Midlertidig. Bestillingen er registrert i systemet og venter på å bli plukket opp for behandling. */
        const val Order_Registered = "Order_Registered"

        /** Midlertidig. Bestillingen er plukket opp og varsler er under generering. */
        const val Order_Processing = "Order_Processing"

        /**
         * Endelig. Alle varsler i bestillingen er ferdig behandlet.
         * Alle mottakere har fått et endelig resultat (levert eller feilet).
         */
        const val Order_Completed = "Order_Completed"

        /**
         * Endelig. Sendebetingelsen ble ikke oppfylt, så ingen varsler ble sendt.
         * Gjelder f.eks. send-condition med KRR-sjekk der mottaker har reservert seg.
         */
        const val Order_SendConditionNotMet = "Order_SendConditionNotMet"

        /** Endelig. Bestillingen ble kansellert før varsler ble sendt. */
        const val Order_Cancelled = "Order_Cancelled"

        /**
         * Midlertidig. Bestillingen er ferdig prosessert – alle varsler er generert.
         * Varsler kan fortsatt være under utsending til mottakere.
         */
        const val Order_Processed = "Order_Processed"

        // ── SMS-statuser ──────────────────────────────────────────────────

        /** Midlertidig. SMS-varselet er opprettet, men ikke ennå sendt til SMS-gateway. */
        const val SMS_New = "SMS_New"

        /** Midlertidig. SMS-varselet er sendt til SMS-gateway og venter på bekreftelse. */
        const val SMS_Sending = "SMS_Sending"

        /** Midlertidig. SMS-gateway har akseptert meldingen, men levering til mottaker er ikke bekreftet ennå. */
        const val SMS_Accepted = "SMS_Accepted"

        /** Endelig. SMS-meldingen er levert til mottakers telefon. */
        const val SMS_Delivered = "SMS_Delivered"

        /** Endelig. Generell feil – SMS-en kunne ikke leveres. */
        const val SMS_Failed = "SMS_Failed"

        /** Endelig. Mottakers telefonnummer er ugyldig. */
        const val SMS_Failed_InvalidRecipient = "SMS_Failed_InvalidRecipient"

        /** Endelig. Mottaker har reservert seg mot elektronisk kommunikasjon i KRR. */
        const val SMS_Failed_RecipientReserved = "SMS_Failed_RecipientReserved"

        /** Endelig. Mottakers nummer er sperret (barred) hos operatør. */
        const val SMS_Failed_BarredReceiver = "SMS_Failed_BarredReceiver"

        /** Endelig. SMS-en ble slettet av operatør eller gateway. */
        const val SMS_Failed_Deleted = "SMS_Failed_Deleted"

        /** Endelig. SMS-en utløp hos operatør/gateway før den kunne leveres. */
        const val SMS_Failed_Expired = "SMS_Failed_Expired"

        /** Endelig. SMS-en kunne ikke leveres til mottaker av ukjent årsak. */
        const val SMS_Failed_Undelivered = "SMS_Failed_Undelivered"

        /** Endelig. Mottaker kunne ikke identifiseres (f.eks. oppslag i kontaktregister feilet). */
        const val SMS_Failed_RecipientNotIdentified = "SMS_Failed_RecipientNotIdentified"

        /** Endelig. SMS-en ble avvist av gateway eller operatør. */
        const val SMS_Failed_Rejected = "SMS_Failed_Rejected"

        /** Endelig. SMS-en overskred time-to-live og ble ikke levert innen tidsfristen. */
        const val SMS_Failed_TTL = "SMS_Failed_TTL"

        // ── E-post-statuser ───────────────────────────────────────────────

        /** Midlertidig. E-postvarselet er opprettet, men ikke ennå sendt til e-posttjenesten. */
        const val Email_New = "Email_New"

        /** Midlertidig. E-posten er sendt til e-posttjenesten og venter på bekreftelse. */
        const val Email_Sending = "Email_Sending"

        /** Midlertidig. E-posttjenesten har akseptert meldingen for levering. Venter på endelig leveringsbekreftelse. */
        const val Email_Succeeded = "Email_Succeeded"

        /** Endelig. E-posten er levert til mottakers innboks. */
        const val Email_Delivered = "Email_Delivered"

        /** Endelig. Generell feil – e-posten kunne ikke leveres. */
        const val Email_Failed = "Email_Failed"

        /** Endelig. Mottaker har reservert seg mot elektronisk kommunikasjon i KRR. */
        const val Email_Failed_RecipientReserved = "Email_Failed_RecipientReserved"

        /** Endelig. Mottaker kunne ikke identifiseres (f.eks. oppslag i kontaktregister feilet). */
        const val Email_Failed_RecipientNotIdentified = "Email_Failed_RecipientNotIdentified"

        /** Endelig. E-postadressen har ugyldig format. */
        const val Email_Failed_InvalidFormat = "Email_Failed_InvalidFormat"

        /** Endelig. Mottaker er undertrykt (suppressed) – e-posttjenesten nekter levering pga. tidligere feil/klager. */
        const val Email_Failed_SuppressedRecipient = "Email_Failed_SuppressedRecipient"

        /** Endelig. Midlertidig feil under sending – e-posttjenesten kunne ikke levere etter gjentatte forsøk. */
        const val Email_Failed_TransientError = "Email_Failed_TransientError"

        /** Endelig. E-posten returnerte (bounced) fra mottakers e-postserver. */
        const val Email_Failed_Bounced = "Email_Failed_Bounced"

        /** Endelig. E-posten ble filtrert som spam av mottakers e-postserver. */
        const val Email_Failed_FilteredSpam = "Email_Failed_FilteredSpam"

        /** Endelig. E-posten ble satt i karantene av mottakers e-postsystem. */
        const val Email_Failed_Quarantined = "Email_Failed_Quarantined"

        /** Endelig. E-posten overskred time-to-live og ble ikke levert innen tidsfristen. */
        const val Email_Failed_TTL = "Email_Failed_TTL"
    }
}


class Altinn3VarselKlientMedFilter(
    private val repository: EksternVarslingRepository,
    private val loggingKlient: Altinn3VarselKlientLogging
) : Altinn3VarselKlientImpl() {

    override suspend fun order(eksternVarsel: EksternVarsel, idempotencyId: String): OrderResponse {
        val mottaker = when (eksternVarsel) {
            is EksternVarsel.Sms -> eksternVarsel.mobilnummer
            is EksternVarsel.Epost -> eksternVarsel.epostadresse
            is EksternVarsel.Altinnressurs -> eksternVarsel.resourceId
            is EksternVarsel.Altinntjeneste -> throw UnsupportedOperationException("Altinntjeneste er ikke støttet")
        }
        return if (repository.mottakerErPåAllowList(mottaker)) {
            super.order(eksternVarsel, idempotencyId)
        } else {
            loggingKlient.order(eksternVarsel, idempotencyId)
        }
    }

    override suspend fun shipment(shipmentId: String): ShipmentResponse {
        return if (repository.ordreHarMottakerPåAllowlist(shipmentId)){
            super.shipment(shipmentId)
        } else {
            loggingKlient.shipment(shipmentId)
        }
    }
}

class Altinn3VarselKlientLogging : Altinn3VarselKlient {
    private val log = logger()

    override suspend fun order(eksternVarsel: EksternVarsel, idempotencyId: String): OrderResponse {
        log.info("order($eksternVarsel)")
        val fakeOrderId = "fake-${UUID.randomUUID()}"
        val fakeShipmentId = "fake-${UUID.randomUUID()}"
        return OrderResponse.Success(
            rå = JsonNodeFactory.instance.objectNode().apply {
                put("notificationOrderId", fakeOrderId)
                putObject("notification").put("shipmentId", fakeShipmentId)
            },
            orderId = fakeOrderId,
            shipmentId = fakeShipmentId,
        )
    }

    override suspend fun shipment(shipmentId: String): ShipmentResponse {
        log.info("shipment($shipmentId)")
        return ShipmentResponse.Success(
            rå = NullNode.instance,
            shipmentId = shipmentId,
            sendersReference = null,
            type = null,
            status = ShipmentStatus.Order_Completed,
            lastUpdate = null,
            recipients = emptyList(),
        )
    }
}