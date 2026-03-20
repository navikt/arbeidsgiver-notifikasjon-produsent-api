package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.jackson.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnPlattformTokenClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Altinn3VarselKlientImplTest {

    @Test
    fun `DTO mapping EksternVarsel Sms - new future orders format`() {
        EksternVarsel.Sms(
            fnrEllerOrgnr = "99999999901",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            mobilnummer = "99999999",
            tekst = "Hei, dette er en test",
            ordreId = "test-idempotency-123"
        ).let { dto ->
            val json = Altinn3VarselKlient.OrderRequest.from(dto, "test-idempotency")
            JSONAssert.assertEquals(
                //language=json
                """
                        {
                          "idempotencyId": "test-idempotency",
                          "recipient": {
                            "recipientSms": {
                              "phoneNumber": "+4799999999",
                              "smsSettings": {
                                "body": "Hei, dette er en test"
                              }
                            }
                          }
                        }
                      """,
                laxObjectMapper.writeValueAsString(json),
                true,
            )
        }
    }

    @Test
    fun `DTO mapping NotificationDTO Sms fikser mobilnummer til gyldig format`() {
        mapOf(
            "40000000" to "+4740000000",
            "004740000000" to "004740000000",
            "+4740000000" to "+4740000000",
            "49999999" to "+4749999999",
            "004749999999" to "004749999999",
            "+4749999999" to "+4749999999",
            "90000000" to "+4790000000",
            "004790000000" to "004790000000",
            "+4790000000" to "+4790000000",
            "99999999" to "+4799999999",
            "004799999999" to "004799999999",
            "+4799999999" to "+4799999999",
        ).forEach { (input, output) ->
            val dto = EksternVarsel.Sms(
                fnrEllerOrgnr = "99999999901",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                mobilnummer = input,
                tekst = "Hei, dette er en test",
                ordreId = "ordre123"
            )
            val json = Altinn3VarselKlient.OrderRequest.from(dto, "test-idempotency")
            JSONAssert.assertEquals(
                //language=json
                """
                        {
                          "idempotencyId": "test-idempotency",
                          "recipient": {
                            "recipientSms": {
                              "phoneNumber": "$output",
                              "smsSettings": {
                                "body": "Hei, dette er en test"
                              }
                            }
                          }
                        }
                          """,
                laxObjectMapper.writeValueAsString(json),
                true
            )
        }
    }

    @Test
    fun `DTO mapping EksternVarsel Epost - new future orders format`() {
        EksternVarsel.Epost(
            fnrEllerOrgnr = "99999999901",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            epostadresse = "foo@bar.baz",
            tittel = "Test",
            body = "Hei, dette er en test",
            ordreId = "test-idempotency-456"
        ).let { dto ->
            val json = Altinn3VarselKlient.OrderRequest.from(dto, "test-idempotency")
            JSONAssert.assertEquals(
                //language=json
                """
                        {
                          "idempotencyId": "test-idempotency",
                          "recipient": {
                            "recipientEmail": {
                              "emailAddress": "foo@bar.baz",
                              "emailSettings": {
                                "fromAddress": "ikke-svar@nav.no",
                                "subject": "Test",
                                "body": "Hei, dette er en test",
                                "contentType": "Html"
                              }
                            }
                          }
                        }
                        """,
                laxObjectMapper.writeValueAsString(json),
                true
            )
        }
    }

    @Test
    fun `DTO mapping EksternVarsel Altinnressurs - new future orders format`() {
        EksternVarsel.Altinnressurs(
            fnrEllerOrgnr = "42",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            resourceId = "nav_foo-bar",
            epostTittel = "Test",
            epostInnhold = "Hei, epost",
            smsInnhold = "Hei, sms",
            ordreId = "test-idempotency-789"
        ).let { dto ->
            val json = Altinn3VarselKlient.OrderRequest.from(dto, "test-idempotency")
            JSONAssert.assertEquals(
                //language=json
                """
                        {
                          "idempotencyId": "test-idempotency",
                          "recipient": {
                            "recipientOrganization": {
                              "orgNumber": "42",
                              "channelSchema": "EmailPreferred",
                              "resourceId": "nav_foo-bar",
                              "resourceAction": "access",
                              "emailSettings": {
                                "fromAddress": "ikke-svar@nav.no",
                                "subject": "Test",
                                "body": "Hei, epost",
                                "contentType": "Html"
                              },
                              "smsSettings": {
                                "body": "Hei, sms"
                              }
                            }
                          }
                        }
                        """,
                laxObjectMapper.writeValueAsString(json),
                true
            )
        }
    }

    @Test
    fun `DTO mapping EksternVarsel Altinntjeneste - UnsupportedOperationException`() {
        val eksternVarsel = EksternVarsel.Altinntjeneste(
            fnrEllerOrgnr = "99999999901",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            serviceCode = "123",
            serviceEdition = "456",
            tittel = "Test",
            innhold = "Hei, dette er en test",
        )

        assertThrows<UnsupportedOperationException> {
            Altinn3VarselKlient.OrderRequest.from(eksternVarsel, "test-idempotency")

        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#order success for sms returnerer order id og shipment id`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = mockOrderResponse,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = Altinn3VarselKlientImpl(
            altinnBaseUrl = "http://altinn",
            httpClient = HttpClient(mockEngine) {
                expectSuccess = true
                install(ContentNegotiation) {
                    jackson()
                }
            },
            altinnPlattformTokenClient = object : AltinnPlattformTokenClient {
                override suspend fun token(scope: String) = "fake-token"
            },
        )
        client.order(eksternVarselSms, "123").let {
            it as Altinn3VarselKlient.OrderResponse.Success
            assertEquals("42", it.orderId)
            assertEquals("shipment-42", it.shipmentId)
        }
        assertEquals(1, mockEngine.requestHistory.size)
        mockEngine.requestHistory.first().let { req ->
            assertEquals("http://altinn/notifications/api/v1/future/orders", req.url.toString())
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("Bearer fake-token", req.headers[HttpHeaders.Authorization])
            JSONAssert.assertEquals(
                //language=json
                """
                    {
                      "idempotencyId": "123",
                      "recipient": {
                        "recipientSms": {
                          "phoneNumber": "+4799999999",
                          "smsSettings": {
                            "body": "Hei, dette er en test"
                          }
                        }
                      }
                    }
                    """,
                req.bodyAsString(),
                true
            )
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#order success for email returnerer order id og shipment id`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = mockOrderResponse,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = Altinn3VarselKlientImpl(
            altinnBaseUrl = "http://altinn",
            httpClient = HttpClient(mockEngine) {
                expectSuccess = true
                install(ContentNegotiation) {
                    jackson()
                }
            },
            altinnPlattformTokenClient = object : AltinnPlattformTokenClient {
                override suspend fun token(scope: String) = "fake-token"
            },
        )
        client.order(eksternVarselEpost, "123").let {
            it as Altinn3VarselKlient.OrderResponse.Success
            assertEquals("42", it.orderId)
            assertEquals("shipment-42", it.shipmentId)
        }
        assertEquals(1, mockEngine.requestHistory.size)
        mockEngine.requestHistory.last().let { req ->
            assertEquals("http://altinn/notifications/api/v1/future/orders", req.url.toString())
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("Bearer fake-token", req.headers[HttpHeaders.Authorization])
            JSONAssert.assertEquals(
                //language=json
                """
                    {
                      "idempotencyId": "123",
                      "recipient": {
                        "recipientEmail": {
                          "emailAddress": "adresse@epost.com",
                          "emailSettings": {
                            "fromAddress": "ikke-svar@nav.no",
                            "subject": "Hei, dette er en tittel",
                            "body": "Hei, dette er en test",
                            "contentType": "Html"
                          }
                        }
                      }
                    }
                    """,
                req.bodyAsString(),
                true
            )
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#order returns error when platform token fails`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = mockOrderResponse,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = Altinn3VarselKlientImpl(
            altinnBaseUrl = "http://altinn",
            httpClient = HttpClient(mockEngine) {
                expectSuccess = true
                install(ContentNegotiation) {
                    jackson()
                }
            },
            altinnPlattformTokenClient = object : AltinnPlattformTokenClient {
                override suspend fun token(scope: String) = throw RuntimeException("Failed to get token")
            },
        )

        client.order(eksternVarselSms, "123").let {
            it as Altinn3VarselKlient.ErrorResponse
            assertEquals("RuntimeException", it.code)
            assertEquals("Failed to get token", it.message)
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#order returns error when http call fails`() = runTest {
        // returns error when http call fails
        val client = Altinn3VarselKlientImpl(
            altinnBaseUrl = "http://altinn",
            httpClient = HttpClient(MockEngine {
                respondError(
                    status = HttpStatusCode.BadRequest,
                    content = """
                                                {
                                                  "type": "string",
                                                  "title": "string",
                                                  "status": 0,
                                                  "detail": "string",
                                                  "instance": "string",
                                                  "errors": {
                                                    "additionalProp1": [
                                                      "string"
                                                    ],
                                                    "additionalProp2": [
                                                      "string"
                                                    ],
                                                    "additionalProp3": [
                                                      "string"
                                                    ]
                                                  },
                                                  "additionalProp1": "string",
                                                  "additionalProp2": "string",
                                                  "additionalProp3": "string"
                                                }
                                            """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }) {
                expectSuccess = true
                install(ContentNegotiation) {
                    jackson()
                }
            },
            altinnPlattformTokenClient = object : AltinnPlattformTokenClient {
                override suspend fun token(scope: String) = "fake-token"
            },
        )
        client.order(eksternVarselSms, "123").let {
            it as Altinn3VarselKlient.ErrorResponse
            assertEquals("400", it.code)
            assertTrue(it.message.contains("Bad Request"))
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#shipment returns delivery manifest`() = runTest {
        val shipmentId = "shipment-42"
        //language=json
        val mockShipmentResponse = """
            {
                "shipmentId": "$shipmentId",
                "sendersReference": null,
                "type": "Notification",
                "status": "Order_Completed",
                "lastUpdate": "2025-03-31T16:21:04.126885Z",
                "recipients": [
                    {
                        "status": "Email_Delivered",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "destination": "recipient@domain.com"
                    },
                    {
                        "status": "SMS_Delivered",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "destination": "+4799999999"
                    }
                ]
            }"""

        val client = newClient(shipmentId, mockShipmentResponse)
        val result = client.shipment(shipmentId)
        result as Altinn3VarselKlient.ShipmentResponse.Success
        assertEquals(shipmentId, result.shipmentId)
        assertEquals("Order_Completed", result.status)
        assertEquals(2, result.recipients.size)
        assertTrue(result.allRecipientsDelivered)
        assertTrue(result.allRecipientsFinished)
        assertTrue(result.isOrderCompleted)

        assertEquals(1, (client.httpClient.engine as MockEngine).requestHistory.size)
        (client.httpClient.engine as MockEngine).requestHistory.first().let { req ->
            assertEquals("http://altinn/notifications/api/v1/future/shipment/$shipmentId", req.url.toString())
            assertEquals(HttpMethod.Get, req.method)
            assertEquals("Bearer fake-token", req.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#shipment with processing recipients`() = runTest {
        val shipmentId = "shipment-43"
        //language=json
        val mockShipmentResponse = """
            {
                "shipmentId": "$shipmentId",
                "sendersReference": null,
                "type": "Notification",
                "status": "Order_Completed",
                "lastUpdate": "2025-03-31T16:21:04.126885Z",
                "recipients": [
                    {
                        "status": "Email_New",
                        "lastUpdate": "2025-03-31T16:21:04.12234Z",
                        "destination": "abc@a.no"
                    },
                    {
                        "status": "Email_Sending",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "destination": "sss.sss@ssss.no"
                    },
                    {
                        "status": "SMS_Accepted",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "destination": "+4799999999"
                    },
                    {
                        "status": "Email_Succeeded",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "destination": "sss.sss@ssss.no"
                    }
                ]
            }"""

        val client = newClient(shipmentId, mockShipmentResponse)
        client.shipment(shipmentId).let {
            it as Altinn3VarselKlient.ShipmentResponse.Success
            assertEquals(4, it.recipients.size)
            assertTrue(it.recipients.all { recipient -> recipient.isProcessing })
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#shipment with failed recipients`() = runTest {
        val shipmentId = "shipment-44"
        //language=json
        val mockShipmentResponse = """
            {
                "shipmentId": "$shipmentId",
                "sendersReference": null,
                "type": "Notification",
                "status": "Order_Completed",
                "lastUpdate": "2025-03-31T16:21:04.126885Z",
                "recipients": [
                    {
                        "status": "Email_Delivered",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "destination": "ok@test.no"
                    },
                    {
                        "status": "Email_Failed_Bounced",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "destination": "bad@test.no"
                    }
                ]
            }"""

        val client = newClient(shipmentId, mockShipmentResponse)
        client.shipment(shipmentId).let {
            it as Altinn3VarselKlient.ShipmentResponse.Success
            assertEquals(2, it.recipients.size)
            assertTrue(it.hasFailedRecipients)
            assertTrue(it.allRecipientsFinished)
            assertTrue(!it.allRecipientsDelivered)
        }
    }
}


//language=json
private const val mockOrderResponse = """
        {
          "notificationOrderId": "42",
          "notification": {
            "shipmentId": "shipment-42",
            "sendersReference": null
          }
        }"""

private val eksternVarselSms = EksternVarsel.Sms(
    fnrEllerOrgnr = "99999999901",
    sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
    sendeTidspunkt = null,
    mobilnummer = "99999999",
    tekst = "Hei, dette er en test",
    ordreId = "123"
)

private val eksternVarselEpost = EksternVarsel.Epost(
    fnrEllerOrgnr = "99999999901",
    sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
    sendeTidspunkt = null,
    epostadresse = "adresse@epost.com",
    tittel = "Hei, dette er en tittel",
    body = "Hei, dette er en test",
    ordreId = "123"
)

/**
 * test helper for getting the body of a client request from mock engine history
 */
private suspend fun HttpRequestData.bodyAsString(): String {
    val channel = ByteChannel()
    (body as OutputStreamContent).writeTo(channel)
    channel.close()
    return channel.readRemaining().readText()
}

private fun newClient(
    shipmentId: String,
    @Language("JSON") shipmentResponse: String
) = Altinn3VarselKlientImpl(
    altinnBaseUrl = "http://altinn",
    httpClient = HttpClient(MockEngine { req ->
        when (req.url.encodedPath) {
            "/notifications/api/v1/future/shipment/$shipmentId" -> respond(
                content = shipmentResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )

            "/notifications/api/v1/future/orders" -> respond(
                content = mockOrderResponse,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )

            else -> error("unexpected request: ${req.url}")
        }
    }) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson()
        }
    },
    altinnPlattformTokenClient = object : AltinnPlattformTokenClient {
        override suspend fun token(scope: String) = "fake-token"
    },
)
