package no.nav.arbeidsgiver.notifikasjon.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.TypedGraphQL
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
import org.intellij.lang.annotations.Language

fun Spec.ktorBrukerTestServer(
    brukerGraphQL: TypedGraphQL<BrukerAPI.Context> = mockk(),
    environment: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): TestApplicationEngine {
    val engine = TestApplicationEngine(
        environment = ApplicationEngineEnvironmentBuilder().build(environment)
    )
    listener(KtorTestListener(engine) {
        httpServerSetup(
            authProviders = listOf(LOCALHOST_BRUKER_AUTHENTICATION),
            extractContext = extractBrukerContext,
            graphql = CompletableDeferred(brukerGraphQL),
        )
    })
    return engine
}

fun Spec.ktorProdusentTestServer(
    produsentGraphQL: TypedGraphQL<ProdusentAPI.Context> = mockk(),
    environment: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): TestApplicationEngine {
    val engine = TestApplicationEngine(
        environment = ApplicationEngineEnvironmentBuilder().build(environment)
    )
    listener(KtorTestListener(engine) {
        httpServerSetup(
            authProviders = listOf(LOCALHOST_PRODUSENT_AUTHENTICATION),
            extractContext = extractProdusentContext,
            graphql = CompletableDeferred(produsentGraphQL)
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
    val brukerAudience = "localhost:bruker-api"
    val produsentAudience = "localhost:bruker-api"

    fun issueToken(
        sub: String,
        audience: String,
    ): String =
        JWT.create().run {
            withIssuer(issuer)
            withSubject(sub)
            withAudience(audience)
            sign(algorithm)
        }

    fun issueProdusentToken(sub: String = "someproducer") =
        issueToken(sub, audience = produsentAudience)

    fun issueBrukerToken(sub: String = "0".repeat(11)) =
        issueToken(sub, audience = brukerAudience)
}

val LOCALHOST_PRODUSENT_AUTHENTICATION = JWTAuthentication(
    name = "localhost",
    config = {
        verifier(
            JWT.require(LocalhostIssuer.algorithm)
                .build()
        )

        validate {
            ProdusentPrincipal(
                subject = it.payload.subject
            )
        }
    }
)

val LOCALHOST_BRUKER_AUTHENTICATION = JWTAuthentication(
    name = "localhost",
    config = {
        verifier(
            JWT.require(LocalhostIssuer.algorithm)
                .build()
        )

        validate {
            BrukerPrincipal(
                fnr = it.payload.subject
            )
        }
    }
)

val SELVBETJENING_TOKEN = LocalhostIssuer.issueBrukerToken()
val TOKENDINGS_TOKEN = LocalhostIssuer.issueProdusentToken()

fun main() {
    println(SELVBETJENING_TOKEN)
    println(TOKENDINGS_TOKEN)
}

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

fun TestApplicationEngine.brukerApi(
    @Language("GraphQL") req: String
): TestApplicationResponse {
    return brukerApi(GraphQLRequest(req))
}
