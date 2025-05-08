package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientStub
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthPluginConfiguration
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenIntrospectionResponse
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.stubProdusentRegister
import org.intellij.lang.annotations.Language
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun ktorBrukerTestServer(
    enhetsregisteret: Enhetsregisteret = EnhetsregisteretStub(),
    brukerRepository: BrukerRepository = BrukerRepositoryStub(),
    kafkaProducer: HendelseProdusent = NoopHendelseProdusent,
    altinnTilgangerService: AltinnTilgangerService = AltinnTilgangerServiceStub(),
    envBlock: ApplicationEnvironmentBuilder.() -> Unit = {},
    testBlock: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    val brukerGraphQL = BrukerAPI.createBrukerGraphQL(
        brukerRepository = brukerRepository,
        hendelseProdusent = kafkaProducer,
        altinnTilgangerService = altinnTilgangerService,
        virksomhetsinfoService = VirksomhetsinfoService(enhetsregisteret),
    )

    environment {
        envBlock()
    }

    application {
        graphqlSetup(
            authPluginConfig = FAKE_BRUKER_AUTHENTICATION,
            extractContext = extractBrukerContext,
            graphql = CompletableDeferred(brukerGraphQL),
        )
    }

    testBlock()
}

fun ktorProdusentTestServer(
    produsentRegister: ProdusentRegister = stubProdusentRegister,
    kafkaProducer: HendelseProdusent = NoopHendelseProdusent,
    produsentRepository: ProdusentRepository = ProdusentRepositoryStub(),
    envBlock: ApplicationEnvironmentBuilder.() -> Unit = {},
    testBlock: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    val produsentGraphQL = ProdusentAPI.newGraphQL(kafkaProducer, produsentRepository)



    environment {
        envBlock()
    }

    application {
        graphqlSetup(
            authPluginConfig = FAKE_PRODUSENT_AUTHENTICATION,
            extractContext = extractProdusentContext(produsentRegister),
            graphql = CompletableDeferred(produsentGraphQL)
        )
    }

    testBlock()
}

const val PRODUSENT_HOST = "ag-notifikasjon-produsent-api.invalid"
const val BRUKER_HOST = "ag-notifikasjon-bruker-api.invalid"

/**
 * fake auth client som returnerer samme token ved introspection.
 */
val fakeAuthClient = object : AuthClientStub() {
    override suspend fun introspect(accessToken: String): TokenIntrospectionResponse =
        laxObjectMapper.readValue(FakeIssuer.decodeFake(accessToken))
}

val FAKE_PRODUSENT_AUTHENTICATION = TexasAuthPluginConfiguration(
    client = fakeAuthClient,
    validate = { token ->
        ProdusentPrincipal.validate(FakeAzurePreAuthorizedApps, token)
    }
)
val FAKE_BRUKER_AUTHENTICATION = TexasAuthPluginConfiguration(
    client = fakeAuthClient,
    validate = { token ->
        BrukerPrincipal.validate(token)
    }
)

val FakeAzurePreAuthorizedApps = object : AzurePreAuthorizedApps {
    override fun lookup(clientId: ClientId) = clientId
}

/**
 * Siden vi bruker texas som tar seg av jwt validering så lager vi fake tokens i testene som
 * bare inneholder de claimene vi bryr oss om.
 */
object FakeIssuer {
    fun issueProdusentToken(sub: String = "someproducer") =
        encodeFake(
            """
            {
              "active": true,
              "azp": "$sub"
            }
        """
        )

    fun issueBrukerToken(sub: String) =
        encodeFake(
            """
            {
              "active": true,
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

fun fakeBrukerApiOboToken(sub: String = TEST_FNR_1) = FakeIssuer.issueBrukerToken(sub)
fun fakeProdusentApiOboToken(sub: String = "someproducer") = FakeIssuer.issueProdusentToken(sub)

suspend fun HttpClient.responseOf(
    method: HttpMethod,
    uri: String,
    host: String? = null,
    accept: String? = null,
    authorization: String? = null,
    config: HttpRequestBuilder.() -> Unit = {}
): HttpResponse =
    request {
        this.method = method
        url(uri)
        if (host != null) {
            header(HttpHeaders.Host, host)
        }
        if (accept != null) {
            header(HttpHeaders.Accept, accept)
        }
        if (authorization != null) {
            header(HttpHeaders.Authorization, authorization)
        }
        config()
    }

suspend fun HttpClient.get(
    uri: String,
    host: String? = null,
    accept: String? = null,
    authorization: String? = null,
    config: HttpRequestBuilder.() -> Unit = {}
) = responseOf(
        HttpMethod.Get,
        uri,
        host = host,
        accept = accept,
        authorization = authorization,
        config = config
    )

suspend fun HttpClient.post(
    uri: String,
    host: String? = null,
    body: String? = null,
    jsonBody: Any? = null,
    accept: String? = null,
    authorization: String? = null,
    config: HttpRequestBuilder.() -> Unit = {},
) = responseOf(
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
                header(HttpHeaders.ContentType, "application/json")
                setBody(laxObjectMapper.writeValueAsString(jsonBody))
            }
            config()
        }
    )

suspend fun HttpClient.brukerApi(
    @Language("GraphQL") req: String,
    fnr: String = TEST_FNR_1,
) = brukerApi(GraphQLRequest(req), fnr)

suspend fun HttpClient.brukerApi(req: GraphQLRequest, fnr: String = TEST_FNR_1) =
    post(
        "/api/graphql",
        host = BRUKER_HOST,
        jsonBody = req,
        accept = "application/json",
        authorization = "Bearer ${fakeBrukerApiOboToken(fnr)}"
    )
