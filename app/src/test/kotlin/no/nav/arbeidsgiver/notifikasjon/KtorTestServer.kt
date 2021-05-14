package no.nav.arbeidsgiver.notifikasjon

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.ktor.application.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*

fun Spec.ktorTestServer(
    brukerGraphQL: TypedGraphQL<BrukerAPI.Context> = mockk(),
    produsentGraphQL: TypedGraphQL<ProdusentAPI.Context> = mockk(),
    environment: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): TestApplicationEngine {
    val engine = TestApplicationEngine(
        environment = ApplicationEngineEnvironmentBuilder().build(environment)
    )
    listener(KtorTestListener(engine) {
        httpServerSetup(
            brukerAutentisering = LOCALHOST_AUTHENTICATION,
            produsentAutentisering = LOCALHOST_AUTHENTICATION,
            brukerGraphQL = brukerGraphQL,
            produsentGraphQL = produsentGraphQL
        )
    })
    return engine
}

class KtorTestListener(
    private val engine: TestApplicationEngine,
    private val init: Application.() -> Unit
) : TestListener {
    override val name: String
        get() = this::class.simpleName!!

    override suspend fun beforeSpec(spec: Spec) {
        engine.start()
        engine.application.apply(init)
    }

    override suspend fun afterSpec(spec: Spec) {
        engine.stop(0L, 0L)
    }
}

const val PRODUSENT_HOST = "ag-notifikasjon-produsent-api.invalid"
const val BRUKER_HOST = "ag-notifikasjon-bruker-api.invalid"

/* Issue tokens as localhost for unit testing */
object LocalhostIssuer {
    val issuer = "localhost"
    val algorithm = Algorithm.none()

    fun issueToken(
        sub: String = "0".repeat(11)
    ): String =
        JWT.create().run {
            withIssuer(issuer)
            withSubject(sub)
            sign(algorithm)
        }
}

private val LOCALHOST_AUTHENTICATION: JWTAuthentication = {
    verifier(JWT.require(LocalhostIssuer.algorithm)
        .withIssuer(LocalhostIssuer.issuer)
        .build()
    )

    validate { credentials ->
        JWTPrincipal(credentials.payload)
    }
}

val SELVBETJENING_TOKEN = LocalhostIssuer.issueToken()
val TOKENDINGS_TOKEN = LocalhostIssuer.issueToken("someproducer")

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

fun TestApplicationEngine.brukerApi(req: GraphQLRequest): TestApplicationResponse {
    return post(
        "/api/graphql",
        host = BRUKER_HOST,
        jsonBody = req,
        accept = "application/json",
        authorization = "Bearer $SELVBETJENING_TOKEN"
    )
}

fun TestApplicationEngine.brukerApi(req: String): TestApplicationResponse {
    return brukerApi(GraphQLRequest(req))
}
