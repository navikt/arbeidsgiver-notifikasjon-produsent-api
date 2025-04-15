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
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.NotificationsResponse.Success
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.NotificationsResponse.Success.Notification
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
    fun `DTO mapping EksternVarsel Sms - NotificationDTO Sms`() {
        EksternVarsel.Sms(
            fnrEllerOrgnr = "99999999901",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            mobilnummer = "99999999",
            tekst = "Hei, dette er en test",
            ordreId = "123"
        ).let { dto ->
            val json = Altinn3VarselKlient.OrderRequest.from(dto)
            JSONAssert.assertEquals(
                //language=json
                """
                        {
                          "notificationChannel" : "Sms",
                          "recipients" : [ {
                            "mobileNumber" : "+4799999999"
                          } ],
                          "smsTemplate" : {
                            "body" : "Hei, dette er en test"
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
                ordreId = "123"
            )
            val json = Altinn3VarselKlient.OrderRequest.from(dto)
            JSONAssert.assertEquals(
                //language=json
                """
                            {
                              "notificationChannel" : "Sms",
                              "recipients" : [ {
                                "mobileNumber" : "$output"
                              } ],
                              "smsTemplate" : {
                                "body" : "Hei, dette er en test"
                              }
                            }
                          """,
                laxObjectMapper.writeValueAsString(json),
                true
            )
        }
    }

    @Test
    fun `DTO mapping EksternVarsel Epost - NotificationDTO Email`() {
        EksternVarsel.Epost(
            fnrEllerOrgnr = "99999999901",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            epostadresse = "foo@bar.baz",
            tittel = "Test",
            body = "Hei, dette er en test",
            ordreId = "123"
        ).let { dto ->
            val json = Altinn3VarselKlient.OrderRequest.from(dto)
            JSONAssert.assertEquals(
                //language=json
                """
                        {
                          "notificationChannel" : "Email",
                          "recipients" : [ {
                            "emailAddress" : "foo@bar.baz"
                          } ],
                          "emailTemplate" : {
                            "subject" : "Test",
                            "body" : "Hei, dette er en test",
                            "contentType" : "Html"
                          }
                        }
                        """,
                laxObjectMapper.writeValueAsString(json),
                true
            )
        }
    }

    @Test
    fun `DTO mapping EksternVarsel Altinnressurs - NotificationDTO Resource`() {
        EksternVarsel.Altinnressurs(
            fnrEllerOrgnr = "42",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            resourceId = "nav_foo-bar",
            epostTittel = "Test",
            epostInnhold = "Hei, epost",
            smsInnhold = "Hei, sms",
            ordreId = "123"
        ).let { dto ->
            val json = Altinn3VarselKlient.OrderRequest.from(dto)
            JSONAssert.assertEquals(
                //language=json
                """
                        {
                          "notificationChannel" : "EmailPreferred",
                          "recipients" : [ {
                            "organizationNumber" : "42"
                          } ],
                          "resourceId" : "nav_foo-bar",
                          "emailTemplate" : {
                            "subject" : "Test",
                            "body" : "Hei, epost",
                            "contentType" : "Html"
                          },
                          "smsTemplate" : {
                            "body" : "Hei, sms"
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
            Altinn3VarselKlient.OrderRequest.from(eksternVarsel)

        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#order success for sms returnerer order id`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
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
        client.order(eksternVarselSms).let {
            it as Altinn3VarselKlient.OrderResponse.Success
            assertEquals("42", it.orderId)
        }
        assertEquals(1, mockEngine.requestHistory.size)
        mockEngine.requestHistory.first().let { req ->
            assertEquals("http://altinn/notifications/api/v1/orders", req.url.toString())
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("Bearer fake-token", req.headers[HttpHeaders.Authorization])
            JSONAssert.assertEquals(
                //language=json
                """
                    {
                      "notificationChannel" : "Sms",
                      "recipients" : [ {
                        "mobileNumber" : "+4799999999"
                      } ],
                      "smsTemplate" : {
                        "body" : "Hei, dette er en test"
                      }
                    }
                    """,
                req.bodyAsString(),
                true
            )
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#order success for email returnerer order id`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
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
        client.order(eksternVarselEpost).let {
            it as Altinn3VarselKlient.OrderResponse.Success
            assertEquals("42", it.orderId)
        }
        assertEquals(1, mockEngine.requestHistory.size)
        mockEngine.requestHistory.last().let { req ->
            assertEquals("http://altinn/notifications/api/v1/orders", req.url.toString())
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("Bearer fake-token", req.headers[HttpHeaders.Authorization])
            JSONAssert.assertEquals(
                //language=json
                """
                    {
                      "notificationChannel": "Email",
                      "emailTemplate": {
                        "subject": "Hei, dette er en tittel",
                        "body": "Hei, dette er en test",
                        "contentType": "Html"
                      },
                      "recipients": [
                        {
                          "emailAddress": "adresse@epost.com"
                        }
                      ]
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
                content = mockResponse,
                status = HttpStatusCode.OK,
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

        client.order(eksternVarselSms).let {
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
        client.order(eksternVarselSms).let {
            it as Altinn3VarselKlient.ErrorResponse
            assertEquals("400", it.code)
            assertTrue(it.message.contains("Bad Request"))
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#notifications`() = runTest {
        val orderId = "42"
        //language=json
        val mockSmsResponse = """
            {
                "orderId": "42",
                "sendersReference": null,
                "generated": 1,
                "succeeded": 1,
                "notifications": [
                    {
                        "id": "4321",
                        "succeeded": false,
                        "recipient": {
                            "mobileNumber": "+4799999999"
                        },
                        "sendStatus": {
                            "status": "New",
                            "description": "The sms has been created, but has not been picked up for processing yet.",
                            "lastUpdate": "2023-11-14T16:06:02.877361Z"
                        }
                    }
                ]
            }"""

        //language=json
        val mockEmailResponse = """
            {
                "orderId": "42",
                "generated": 1,
                "succeeded": 1,
                "notifications": [
                    {
                        "id": "1234",
                        "succeeded": false,
                        "recipient": {
                            "emailAddress": "recipient@domain.com"
                        },
                        "sendStatus": {
                            "status": "New",
                            "description": "The email has been created, but has not been picked up for processing yet.",
                            "lastUpdate": "2023-11-14T16:06:02.877361Z"
                        }
                    }
                ]
            }"""

        // gets sms and email notifications for a given order id
        val client = newClient(orderId, mockSmsResponse, mockEmailResponse)
        val result = client.notifications(orderId)
        result as Success
        assertEquals(orderId, result.orderId)
        assertEquals(null, result.sendersReference)
        assertEquals(2, result.generated)
        assertEquals(2, result.succeeded)
        assertEquals(2, result.notifications.size)
        assertEquals(
            listOf(
                Notification(
                    id = "4321",
                    succeeded = false,
                    recipient = Notification.Recipient(
                        mobileNumber = "+4799999999"
                    ),
                    sendStatus = Notification.SendStatus(
                        status = "New",
                        description = "The sms has been created, but has not been picked up for processing yet.",
                        lastUpdate = "2023-11-14T16:06:02.877361Z"
                    )
                ),
                Notification(
                    id = "1234",
                    succeeded = false,
                    recipient = Notification.Recipient(
                        emailAddress = "recipient@domain.com"
                    ),
                    sendStatus = Notification.SendStatus(
                        status = "New",
                        description = "The email has been created, but has not been picked up for processing yet.",
                        lastUpdate = "2023-11-14T16:06:02.877361Z"
                    )
                ),
            ),
            result.notifications
        )
        assertEquals(2, (client.httpClient.engine as MockEngine).requestHistory.size)
        (client.httpClient.engine as MockEngine).requestHistory.first().let { req ->
            assertEquals("http://altinn/notifications/api/v1/orders/$orderId/notifications/sms", req.url.toString())
            assertEquals(HttpMethod.Get, req.method)
            assertEquals("Bearer fake-token", req.headers[HttpHeaders.Authorization])
        }
        (client.httpClient.engine as MockEngine).requestHistory.last().let { req ->
            assertEquals("http://altinn/notifications/api/v1/orders/$orderId/notifications/email", req.url.toString())
            assertEquals(HttpMethod.Get, req.method)
            assertEquals("Bearer fake-token", req.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `Altinn3VarselKlientImpl#notifications no sms only email`() = runTest {
        // gets sms and email notifications for a given order id
        val client = newClient(
            "43",
            smsResponse = """
                {
                  "generated": 0,
                  "notifications": [],
                  "orderId": "70bc6dff-0c14-4842-b36a-3ab64a2dae08",
                  "succeeded": 0
                }""",
            emailResponse = """
                {
                  "generated": 2,
                  "notifications": [
                    {
                      "id": "042f1a8a-fe29-47c9-a992-433399563c12",
                      "recipient": {
                        "emailAddress": "abc@a.no",
                        "organizationNumber": "211511052"
                      },
                      "sendStatus": {
                        "description": "The email has been created, but has not been picked up for processing yet.",
                        "lastUpdate": "2025-03-31T16:21:04.12234Z",
                        "status": "New"
                      },
                      "succeeded": false
                    },
                    {
                      "id": "6b3bfbc4-a092-4df7-8f38-e43171df49a9",
                      "recipient": {
                        "emailAddress": "sss.sss@ssss.no",
                        "organizationNumber": "211511052"
                      },
                      "sendStatus": {
                        "description": "The email has been created, but has not been picked up for processing yet.",
                        "lastUpdate": "2025-03-31T16:21:04.126885Z",
                        "status": "New"
                      },
                      "succeeded": false
                    }
                  ],
                  "orderId": "70bc6dff-0c14-4842-b36a-3ab64a2dae08",
                  "succeeded": 0
                }"""
        )
        client.notifications("43").let {
            it as Success
            assertEquals(2, it.generated)
            assertEquals(0, it.succeeded)
            assertEquals(2, it.notifications.size)
            assertTrue(it.notifications.any { notification -> notification.sendStatus.isProcessing })
        }
    }
}


//language=json
private const val mockResponse = """
        {
          "orderId": "42",
          "recipientLookup": {
            "status": "Success",
            "isReserved": [
              "string"
            ],
            "missingContact": [
              "string"
            ]
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
    orderId: String,
    @Language("JSON") smsResponse: String,
    @Language("JSON") emailResponse: String
) = Altinn3VarselKlientImpl(
    altinnBaseUrl = "http://altinn",
    httpClient = HttpClient(MockEngine { req ->
        when (req.url.encodedPath) {
            "/notifications/api/v1/orders/$orderId/notifications/sms" -> respond(
                content = smsResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )

            "/notifications/api/v1/orders/$orderId/notifications/email" -> respond(
                content = emailResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )

            "/notifications/api/v1/orders" -> respond(
                //language=json
                content = """
                            {
                              "orderId": "42",
                              "recipientLookup": {
                                "status": "Success",
                                "isReserved": [
                                  "string"
                                ],
                                "missingContact": [
                                  "string"
                                ]
                              }
                            }""",
                status = HttpStatusCode.OK,
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
