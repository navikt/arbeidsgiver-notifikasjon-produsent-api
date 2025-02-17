package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CompletableDeferred
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepository
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_FNR_1
import no.nav.arbeidsgiver.notifikasjon.bruker.VirksomhetsinfoService
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.AzurePreAuthorizedApps
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.ClientId
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthPluginConfiguration
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenIntrospectionResponse
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.stubProdusentRegister
import org.intellij.lang.annotations.Language
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun Spec.ktorBrukerTestServer(
    enhetsregisteret: Enhetsregisteret = EnhetsregisteretStub(),
    brukerRepository: BrukerRepository = BrukerRepositoryStub(),
    kafkaProducer: HendelseProdusent = NoopHendelseProdusent,
    altinnTilgangerService: AltinnTilgangerService = AltinnTilgangerServiceStub(),
    environment: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): TestApplicationEngine {
    val engine = TestApplicationEngine(
        environment = ApplicationEngineEnvironmentBuilder().build(environment)
    )
    val brukerGraphQL = BrukerAPI.createBrukerGraphQL(
        brukerRepository = brukerRepository,
        hendelseProdusent = kafkaProducer,
        altinnTilgangerService = altinnTilgangerService,
        virksomhetsinfoService = VirksomhetsinfoService(enhetsregisteret),
    )
    listener(KtorTestListener(engine))
    engine.start()
    engine.application.apply {
        graphqlSetup(
            authPluginConfig = FAKE_BRUKER_AUTHENTICATION,
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
            authPluginConfig = FAKE_PRODUSENT_AUTHENTICATION,
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

val FAKE_PRODUSENT_AUTHENTICATION = TexasAuthPluginConfiguration(
    client = object : AuthClient {
        override suspend fun token(target: String) = TODO()
        override suspend fun exchange(target: String, userToken: String) = TODO()

        override suspend fun introspect(accessToken: String) = TokenIntrospectionResponse(
            active = true,
            error = null,
            other = laxObjectMapper.readValue(FakeIssuer.decodeFake(accessToken))
        )
    },
    validate = { token ->
        ProdusentPrincipal.validate(FakeAzurePreAuthorizedApps, token)
    }
)

val FAKE_BRUKER_AUTHENTICATION = TexasAuthPluginConfiguration(
    client = object : AuthClient {
        override suspend fun token(target: String) = TODO("not implemented")
        override suspend fun exchange(target: String, userToken: String) = TODO("not implemented")

        override suspend fun introspect(accessToken: String) = TokenIntrospectionResponse(
            active = true,
            error = null,
            other = laxObjectMapper.readValue(FakeIssuer.decodeFake(accessToken))
        )
    },
    validate = { token ->
        BrukerPrincipal.validate(token)
    }
)

val FakeAzurePreAuthorizedApps = object : AzurePreAuthorizedApps {
    override fun lookup(clientId: ClientId) = clientId
}

object FakeIssuer {
    fun issueProdusentToken(sub: String = "someproducer") =
        encodeFake(
            """
            {
              "azp": "$sub"
            }
        """
        )

    fun issueBrukerToken(sub: String = TEST_FNR_1) =
        encodeFake(
            """
            {
              "pid": "$sub",
              "acr": "idporten-loa-high"
            }
            """
        )

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeFake(str: String) = Base64.Default.encode(str.encodeToByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeFake(str: String) = Base64.Default.decode(str)
}

val BRUKERAPI_OBOTOKEN = FakeIssuer.issueBrukerToken()
val PRODUSENTAPI_AZURETOKEN = FakeIssuer.issueProdusentToken()

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
        authorization = "Bearer $BRUKERAPI_OBOTOKEN"
    )
}

fun TestApplicationEngine.brukerApi(
    @Language("GraphQL") req: String
): TestApplicationResponse {
    return brukerApi(GraphQLRequest(req))
}
