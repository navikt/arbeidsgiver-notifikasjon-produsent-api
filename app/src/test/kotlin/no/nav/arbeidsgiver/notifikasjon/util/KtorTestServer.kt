package no.nav.arbeidsgiver.notifikasjon.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CompletableDeferred
import no.nav.arbeidsgiver.notifikasjon.bruker.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.stubProdusentRegister
import org.intellij.lang.annotations.Language

fun Spec.ktorBrukerTestServer(
    enhetsregisteret: Enhetsregisteret = EnhetsregisteretStub(),
    brukerRepository: BrukerRepository = BrukerRepositoryStub(),
    kafkaProducer: HendelseProdusent = NoopHendelseProdusent,
    altinn: Altinn = AltinnStub(),
    tilgangerService: TilgangerService = TilgangerServiceImpl(altinn),
    environment: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): TestApplicationEngine {
    val engine = TestApplicationEngine(
        environment = ApplicationEngineEnvironmentBuilder().build(environment)
    )
    val brukerGraphQL = BrukerAPI.createBrukerGraphQL(
        brukerRepository = brukerRepository,
        hendelseProdusent = kafkaProducer,
        tilgangerService = tilgangerService,
        virksomhetsinfoService = VirksomhetsinfoService(enhetsregisteret),
    )
    listener(KtorTestListener(engine))
    engine.start()
    engine.application.apply {
        graphqlSetup(
            authProviders = listOf(LOCALHOST_BRUKER_AUTHENTICATION),
            extractContext = extractBrukerContext,
            graphql = CompletableDeferred(brukerGraphQL),
        )
    }
    return engine
}

fun Spec.ktorProdusentTestServer(
    produsentRegister: ProdusentRegister = stubProdusentRegister,
    kafkaProducer: HendelseProdusent = NoopHendelseProdusent,
    produsentRepository: ProdusentRepository = ProdusentRepositoryStub(),
    environment: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): TestApplicationEngine {
    val engine = TestApplicationEngine(
        environment = ApplicationEngineEnvironmentBuilder().build(environment)
    )
    val produsentGraphQL = ProdusentAPI.newGraphQL(kafkaProducer, produsentRepository)
    listener(KtorTestListener(engine))
    engine.start()
    engine.application.apply {
        graphqlSetup(
            authProviders = listOf(LOCALHOST_PRODUSENT_AUTHENTICATION),
            extractContext = extractProdusentContext(produsentRegister),
            graphql = CompletableDeferred(produsentGraphQL)
        )
    }
    return engine
}

class KtorTestListener(
    private val engine: TestApplicationEngine
) : TestListener {

    override suspend fun beforeSpec(spec: Spec) {
    }

    override suspend fun afterSpec(spec: Spec) {
        engine.stop(0L, 0L)
    }
}

const val PRODUSENT_HOST = "ag-notifikasjon-produsent-api.invalid"
const val BRUKER_HOST = "ag-notifikasjon-bruker-api.invalid"

/* Issue tokens as localhost for unit testing */
object LocalhostIssuer {
    private const val issuer = "localhost"
    val algorithm: Algorithm = Algorithm.none()
    private const val brukerAudience = "localhost:bruker-api"
    private const val produsentAudience = "localhost:bruker-api"

    private fun issueToken(
        sub: String,
        audience: String,
        azp: String? = null
    ): String =
        JWT.create().run {
            withIssuer(issuer)
            withSubject(sub)
            withAudience(audience)
            if (azp != null) {
                withClaim("azp", azp)
            }
            sign(algorithm)
        }

    fun issueProdusentToken(sub: String = "someproducer") =
        issueToken(
            sub,
            audience = produsentAudience,
            azp = sub
        )

    fun issueBrukerToken(sub: String = TEST_FNR_1) =
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
                appName = it.payload.getClaim("azp").asString()
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
                setBody(laxObjectMapper.writeValueAsString(jsonBody))
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
