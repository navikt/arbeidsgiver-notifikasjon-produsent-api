package no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.utils.io.*

class TexasTest : DescribeSpec({
    describe("TokenResponse") {
        it("secrets er ikke del av toString") {
            val secret = "12355jkasdklajsflajflj"
            val tokenEndpointResponse = TokenResponse.Success(accessToken = secret, expiresInSeconds = 33)

            tokenEndpointResponse.toString() shouldNotContain secret
        }
    }

    describe("TokenIntrospectionResponse") {
        //language=json
        val mockTokenResponse = """
           {
              "active": true,
              "aud": "my-target",
              "azp": "yolo",
              "exp": 1730980893,
              "iat": 1730977293,
              "iss": "http://localhost:8080/tokenx",
              "jti": "jti",
              "nbf": 1730977293,
              "sub": "e015542c-0f81-40f5-bbd9-7c3d9366298f",
              "tid": "tokenx",
              "pid": "pid",
              "acr": "idporten-loa-high"
          } 
        """
        val authClient = AuthClientImpl(
            TexasAuthConfig("", "", ""),
            IdentityProvider.TOKEN_X,
            httpClient = HttpClient(
                MockEngine { req ->
                    respond(
                        content = ByteReadChannel(mockTokenResponse),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            ) {
                install(ContentNegotiation) {
                    jackson()
                }
            }
        )

        it("har claims vi bryr oss om") {
            authClient.introspect("token").let {
                it.active shouldBe true
                it.pid shouldBe "pid"
                it.azp shouldBe "yolo"
                it.acr shouldBe "idporten-loa-high"

                // TODO: @JsonAnySetter broken i jackson 2.17.0-2.18.1
                //it.other["jti"] shouldBe "jti"
            }
        }
    }

    describe("AuthClient") {
        //example respones from https://github.com/nais/texas
        val responses = mapOf(
            "/token" to
                    //language=json
                    """
                    {
                      "access_token": "<some-access-token>",
                      "expires_in": 3599,
                      "token_type": "Bearer"
                    }
                    """,

            "/exchange" to
                    //language=json
                    """
                    {
                      "access_token": "<some-access-token>",
                      "expires_in": 3599,
                      "token_type": "Bearer"
                    }
                    """,

            "/introspect" to
                    //language=json
                    """
                    { 
                      "active": true,
                      "aud": "my-target",
                      "azp": "yolo",
                      "exp": 1730980893,
                      "iat": 1730977293,
                      "iss": "http://localhost:8080/tokenx",
                      "jti": "e7cbadc3-6bda-49c0-a196-c47328da880e",
                      "nbf": 1730977293,
                      "sub": "e015542c-0f81-40f5-bbd9-7c3d9366298f",
                      "tid": "tokenx"
                    } 
                    """
        )

        val authClient = AuthClientImpl(
            TexasAuthConfig(
                tokenEndpoint = "/token",
                tokenExchangeEndpoint = "/exchange",
                tokenIntrospectionEndpoint = "/introspect"
            ),
            IdentityProvider.TOKEN_X,
            httpClient = HttpClient(
                MockEngine { req ->
                    respond(
                        content = ByteReadChannel(responses[req.url.encodedPath]!!),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            ) {
                install(ContentNegotiation) {
                    jackson()
                }
            }
        )

        it("token") {
            authClient.token("") shouldNotBe null
        }

        it("exchange") {
            authClient.exchange("", "") shouldNotBe null
        }

        it("introspect") {
            authClient.introspect("") shouldNotBe null
        }

        it ("error response") {
            AuthClientImpl(
                TexasAuthConfig("", "", ""),
                IdentityProvider.TOKEN_X,
                httpClient = HttpClient(
                    MockEngine { _ ->
                        respond(
                            content = ByteReadChannel(
                                //language=json
                                """
                                {
                                  "error": "invalid_token",
                                  "error_description": "womp womp, bc reasons.. lol"
                                }
                                """
                            ),
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                ) {
                    install(ContentNegotiation) {
                        jackson()
                    }
                }
            ).let {
                it.introspect("") shouldNotBe null
            }


        }
    }
})
