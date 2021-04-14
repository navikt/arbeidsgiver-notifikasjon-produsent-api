package no.nav.arbeidsgiver.notifikasjon

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import io.kotest.core.TestConfiguration
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.spyk
import no.nav.arbeidsgiver.notifikasjon.graphql.produsentGraphQL
import kotlin.reflect.KProperty

const val PRODUSENT_HOST = "ag-notifikasjon-produsent-api.invalid"
const val BRUKER_HOST = "ag-notifikasjon-bruker-api.invalid"

const val tokenDingsToken =
    "eyJraWQiOiJtb2NrLW9hdXRoMi1zZXJ2ZXIta2V5IiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJzb21lcHJvZHVjZXIiLCJhdWQiOiJwcm9kdXNlbnQtYXBpIiwibmJmIjoxNjE2MDY4MjQ3LCJpc3MiOiJodHRwczpcL1wvZmFrZWRpbmdzLmRldi1nY3AubmFpcy5pb1wvZmFrZSIsImV4cCI6MTYxOTY2ODI0NywiaWF0IjoxNjE2MDY4MjQ3LCJqdGkiOiJmNjY0MDU2Ny05YTBjLTQwM2QtOGE3MC1lMjY5MWFjNTBlMDgifQ.BHN7JJZYAwn-zvk_YqshikYbZ2GgFprBhJxgZvjSjIuoZ76ctXOOdlGdxlpYQTTnFLeCmVclAmhFgr0uYa5R0W1sWNz9wTb7m02QosPRDg_uDZA9KLuQH-YaKTzCGwagH93_ytnjj5nVO6HW2wjZafDW9ZPcBIzZxeUOgBUoVULS2SM0joRxMLTbMTQQhpanR0Ly1peUdeUJTrb89XHR7lSLIMrxI15CMabvY6uV2ftR-oub38NGC3SHHoTft665lUwe3hKlfib4YxPbvSA0lguYXPs7LQcvoTu86DO93_la2-t8SovjEY4dy8Sa6mn_IqS8DlJzGUIlkj5P2vptxQ"

const val selbetjeningsToken =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIwMDAwMDAwMDAwMCIsImF1ZCI6ImF1ZC1zZWx2YmV0amVuaW5nIiwidmVyIjoiMS4wIiwibmJmIjoxNTc4NTc2NDQ4LCJhdXRoX3RpbWUiOjE1Nzg1NzY0NDgsImlzcyI6InNlbHZiZXRqZW5pbmciLCJleHAiOjIzNTYxNzY0NDgsIm5vbmNlIjoibXlOb25jZSIsImlhdCI6MTU3ODU3NjQ0OCwianRpIjoiYjE3NTZlZGUtNjQxNy00ZTk2LTg5ODYtM2M5NzllOTkxMzEzIiwiYWNyIjoiTGV2ZWw0In0.Vn5Y0HAei856w1zO3T8M_9iQM0rAWxuUMr8WARy2DIgIEvPff5tss2BuxiFjNcm9BHyJu83ADeqw-YXgSLLw8HvwyfRWPiANT5oCsfldtvZwNjXG2i-VbAV2LYl1jDF_aB7IgtzH-bDjgq2xr5OemPdBbmhEmSkLYr0K-kbPYB6wYV5FjC9LPhdz6XtB5htl3ttyaDUSZLg9-m6FycV2R5bUxyNQB8VBeKB405gdmbOad7WQYmgKJLaB-cAntHJb3z1RUeHkBP21J2H6BJp_MlyK87lx1tN5K6sBN1M5wvY1-ZGqawz11GlijYxwPsLCmM9IkmOlzQaVkgUpI6H5GQ"

val noopVerifierConfig: JWTAuthConfig = {
    verifier(object : JWTVerifier {
        override fun verify(p0: String?): DecodedJWT {
            return JWT.decode(p0 ?: tokenDingsToken)
        }

        override fun verify(p0: DecodedJWT?): DecodedJWT {
            return verify("")
        }
    })
    validate {credentials -> JWTPrincipal(credentials.payload)}
}

class TestApplicationEngineDelegate(context: TestConfiguration) {
    lateinit var engine: TestApplicationEngine

    init {
        context.aroundTest { test ->
            engine = TestApplicationEngine(createTestEnvironment {
                log = spyk(log)
            })
            engine.start()
            try {
                engine.run {
                    application.module(
                        authenticationConfiguration = (noopVerifierConfig),
                        produsentGraphql = produsentGraphQL(
                            kafkaProducer = context.kafkaEnv().producer
                        )
                    )
                    val (arg, body) = test
                    body.invoke(arg)
                }
            } finally {
                engine.stop(0L, 0L)
            }
        }
    }
    operator fun getValue(thisRef: Any?, property: KProperty<*>): TestApplicationEngine {
        return engine
    }
}

fun TestConfiguration.ktorEngine() = TestApplicationEngineDelegate(this)

typealias RequestConfig = TestApplicationRequest.() -> Unit

fun TestApplicationEngine.responseOf(
    method: HttpMethod,
    uri: String,
    host: String? = null,
    accept: String? = null,
    authorization: String? = null,
    config: RequestConfig = {}
): TestApplicationResponse =
    handleRequest(method, uri) {
        if (host != null) {
            addHeader(HttpHeaders.Host, host)
        }
        if (accept != null) {
            addHeader(HttpHeaders.Accept, accept)
        }
        if (authorization != null) {
            addHeader(HttpHeaders.Authorization, authorization)
        }
        config()
    }.response

fun TestApplicationEngine.get(
    uri: String,
    host: String? = null,
    accept: String? = null,
    authorization: String? = null,
    config: RequestConfig = {}
): TestApplicationResponse =
    responseOf(
        HttpMethod.Get,
        uri,
        host = host,
        accept = accept,
        authorization = authorization,
        config = config
    )

fun TestApplicationEngine.post(
    uri: String,
    host: String? = null,
    body: String? = null,
    jsonBody: Any? = null,
    accept: String? = null,
    authorization: String? = null,
    config: RequestConfig = {},
): TestApplicationResponse =
    responseOf(
        HttpMethod.Post,
        uri,
        host = host,
        accept = accept,
        authorization = authorization,
        config = {
            if (body != null) {
                setBody(body)
            }
            if (jsonBody != null) {
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody(objectMapper.writeValueAsString(jsonBody))
            }
            config()
        }
    )
