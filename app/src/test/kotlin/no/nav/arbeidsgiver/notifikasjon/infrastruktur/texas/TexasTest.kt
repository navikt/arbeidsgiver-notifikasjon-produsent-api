package no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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

})
