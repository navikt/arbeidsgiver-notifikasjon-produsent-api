package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.jackson.*
import io.ktor.utils.io.*
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.NotificationsResponse.Success
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.NotificationsResponse.Success.Notification
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnPlattformTokenClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper

class Altinn3VarselKlientImplTest : DescribeSpec({

    describe("DTO mapping") {
        it("EksternVarsel.Sms -> NotificationDTO.Sms") {
            EksternVarsel.Sms(
                fnrEllerOrgnr = "12345678901",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                mobilnummer = "12345678",
                tekst = "Hei, dette er en test",
            ).let { dto ->
                val json = Altinn3VarselKlient.OrderRequest.from(dto)
                laxObjectMapper.writeValueAsString(json) shouldEqualJson //language=json
                        """
                        {
                          "notificationChannel" : "Sms",
                          "recipients" : [ {
                            "mobileNumber" : "12345678"
                          } ],
                          "smsTemplate" : {
                            "body" : "Hei, dette er en test"
                          }
                        }
                      """
            }
        }

        it("EksternVarsel.Epost -> NotificationDTO.Email") {
            EksternVarsel.Epost(
                fnrEllerOrgnr = "12345678901",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                epostadresse = "foo@bar.baz",
                tittel = "Test",
                body = "Hei, dette er en test",
            ).let { dto ->
                val json = Altinn3VarselKlient.OrderRequest.from(dto)
                laxObjectMapper.writeValueAsString(json) shouldEqualJson
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
                        """
            }
        }

        it("EksternVarsel.Altinnressurs -> NotificationDTO.Resource") {
            EksternVarsel.Altinnressurs(
                fnrEllerOrgnr = "42",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                resourceId = "nav_foo-bar",
                epostTittel = "Test",
                epostInnhold = "Hei, epost",
                smsInnhold = "Hei, sms",
            ).let { dto ->
                val json = Altinn3VarselKlient.OrderRequest.from(dto)
                laxObjectMapper.writeValueAsString(json) shouldEqualJson
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
                        """
            }
        }

        it("EksternVarsel.Altinntjeneste -> UnsupportedOperationException") {
            val eksternVarsel = EksternVarsel.Altinntjeneste(
                fnrEllerOrgnr = "12345678901",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                serviceCode = "123",
                serviceEdition = "456",
                tittel = "Test",
                innhold = "Hei, dette er en test",
            )

            shouldThrow<UnsupportedOperationException> {
                Altinn3VarselKlient.OrderRequest.from(eksternVarsel)
            }
        }
    }

    describe("Altinn3VarselKlientImpl#order") {
        //language=json
        val mockResponse = """
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
        val mockEngine = MockEngine {
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val eksternVarsel = EksternVarsel.Sms(
            fnrEllerOrgnr = "12345678901",
            sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            mobilnummer = "12345678",
            tekst = "Hei, dette er en test",
        )


        it("success returnerer order id") {
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
            client.order(eksternVarsel) shouldBe Altinn3VarselKlient.OrderResponse.Success("42")
            mockEngine.requestHistory.size shouldBe 1
            mockEngine.requestHistory.first().let { req ->
                req.url.toString() shouldBe "http://altinn/notifications/api/v1/orders"
                req.method shouldBe HttpMethod.Post
                req.headers[HttpHeaders.Authorization] shouldBe "Bearer fake-token"
                req.bodyAsString() shouldEqualJson //language=json
                    """
                    {
                      "notificationChannel" : "Sms",
                      "recipients" : [ {
                        "mobileNumber" : "12345678"
                      } ],
                      "smsTemplate" : {
                        "body" : "Hei, dette er en test"
                      }
                    }
                    """
            }
        }

        it("returns error when platform token fails") {
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

            client.order(eksternVarsel) shouldBe Altinn3VarselKlient.ErrorResponse("Failed to get token", "RuntimeException")
        }

        it("returns error when http call fails") {
            val client = Altinn3VarselKlientImpl(
                altinnBaseUrl = "http://altinn",
                httpClient = HttpClient(MockEngine {
                    respondError(HttpStatusCode.BadRequest)
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

            client.order(eksternVarsel) shouldBe Altinn3VarselKlient.ErrorResponse("Bad Request", "400")
        }
    }

    describe("Altinn3VarselKlientImpl#notifications") {
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
                "sendersReference": null,
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


        fun newClient() = Altinn3VarselKlientImpl(
            altinnBaseUrl = "http://altinn",
            httpClient = HttpClient(MockEngine { req ->
                when (req.url.encodedPath) {
                    "/notifications/api/v1/orders/$orderId/notifications/sms" -> respond(
                        content = mockSmsResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )

                    "/notifications/api/v1/orders/$orderId/notifications/email" -> respond(
                        content = mockEmailResponse,
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

        it("gets sms and email notifications for a given order id") {
            val client = newClient()
            val result = client.notifications(orderId)
            result shouldBe Success(
                    orderId = orderId,
                    sendersReference = null,
                    generated = 2,
                    succeeded = 2,
                    notifications = listOf(
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
                    )
                )
            (client.httpClient.engine as MockEngine).requestHistory.size shouldBe 2
            (client.httpClient.engine as MockEngine).requestHistory.first().let { req ->
                req.url.toString() shouldBe "http://altinn/notifications/api/v1/orders/$orderId/notifications/sms"
                req.method shouldBe HttpMethod.Get
                req.headers[HttpHeaders.Authorization] shouldBe "Bearer fake-token"
            }
            (client.httpClient.engine as MockEngine).requestHistory.last().let { req ->
                req.url.toString() shouldBe "http://altinn/notifications/api/v1/orders/$orderId/notifications/email"
                req.method shouldBe HttpMethod.Get
                req.headers[HttpHeaders.Authorization] shouldBe "Bearer fake-token"
            }
        }
    }
})

/**
 * test helper for getting the body of a client request from mock engine history
 */
private suspend fun HttpRequestData.bodyAsString(): String {
    val channel = ByteChannel()
    (body as OutputStreamContent).writeTo(channel)
    channel.close()
    return channel.readRemaining().readText()
}
