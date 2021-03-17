package no.nav.arbeidsgiver.notifikasjon

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import graphql.ExecutionInput.newExecutionInput
import graphql.GraphQL.newGraphQL
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates.coordinates
import graphql.schema.GraphQLCodeRegistry.newCodeRegistry
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.metrics.micrometer.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runBlocking
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.event.Level
import java.net.ProxySelector
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

data class GraphQLJsonBody(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, String>? = null
)

data class World(val greeting: String)
data class Addition(val sum: Int)

data class AzureAdOpenIdConfiguration(
    val jwks_uri: String,
    val issuer: String,
    val token_endpoint: String,
    val authorization_endpoint: String
)

val clientId = System.getenv("AZURE_APP_CLIENT_ID")
val wellKnownUrl = System.getenv("AZURE_APP_WELL_KNOWN_URL")
val jwksUri = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")
val openIdConfiguration: AzureAdOpenIdConfiguration = runBlocking {
     client.get<AzureAdOpenIdConfiguration>(wellKnownUrl)
}

fun graphQLExecuter(): (request: GraphQLJsonBody) -> Any {
    val worldFetcher = DataFetcher {
        World("Hello world!")
    }

    val additionFetcher = DataFetcher {
        val a = it.getArgument<Int>("a")
        val b = it.getArgument<Int>("b")
        Addition(a + b)
    }

    val codeRegistry = newCodeRegistry()
        .dataFetcher(coordinates("Query", "world"), worldFetcher)
        .dataFetcher(coordinates("Query", "addition"), additionFetcher)
        .build()

    val schema = SchemaGenerator().makeExecutableSchema(
        SchemaParser().parse({}.javaClass.getResourceAsStream("/schema.graphqls")),
        RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistry).build()
    )

    val graphql = newGraphQL(schema).build()

    return { request ->
        graphql.execute(
            newExecutionInput().apply {
                val (query, name, vars) = request
                query(query)

                if (name != null) {
                    operationName(name)
                }

                if (vars != null) {
                    variables(vars)
                }
            }.build()
        ).toSpecification()
    }
}

val client = HttpClient(Apache){
    install(JsonFeature) {
        serializer = JacksonSerializer {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    engine {
        customizeClient { setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault())) }
    }
}

fun Application.module() {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val graphql = graphQLExecuter()

    install(MicrometerMetrics) {
        registry = meterRegistry
    }

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        retrieveFromHeader(HttpHeaders.XCorrelationId)
        retrieveFromHeader("call-id")
        retrieveFromHeader("callId")
        retrieveFromHeader("call_id")

        generate {
            UUID.randomUUID().toString()
        }

        replyToHeader(HttpHeaders.XCorrelationId)
    }

    install(CallLogging) {
        filter { call ->
            !call.request.path().startsWith("/internal/")
        }

        level = Level.INFO
        mdc("method") { call ->
            call.request.httpMethod.value
        }
        mdc("path") { call ->
            call.request.path()
        }
        callIdMdc("x_correlation_id")
    }

    install(StatusPages) {
        exception<Throwable> { ex ->
            if (ex is JsonProcessingException) {
                // ikke logg json-teksten som feilet.
                ex.clearLocation()
            }

            log.warn("unhandle exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
            call.respond(
                HttpStatusCode.InternalServerError, mapOf(
                "error" to "unexpected error",
            )
            )
        }
    }

    install(ContentNegotiation) {
        jackson()
    }

    val jwkProvider = JwkProviderBuilder(URL(jwksUri))
        .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
        .rateLimited(10, 1, TimeUnit.MINUTES) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .build()

    install(Authentication) {
        jwt {
            verifier(jwkProvider, openIdConfiguration.issuer)
            validate { credentials ->
                try {
                    requireNotNull(credentials.payload.audience) {
                        "Auth: Missing audience in token"
                    }
                    require(credentials.payload.audience.contains(clientId)) {
                        "Auth: Valid audience not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }

    routing {
        route("internal") {
            get("alive") {
                call.respond(HttpStatusCode.OK)
            }
            get("ready") {
                call.respond(HttpStatusCode.OK)
            }

            get("metrics") {
                call.respond(meterRegistry.scrape())
            }
        }
        authenticate {
            route("api") {
                get("ide") {
                    call.respondBytes(
                        """
                    <html>
                      <head>
                        <title>Simple GraphiQL Example</title>
                        <link href="https://unpkg.com/graphiql/graphiql.min.css" rel="stylesheet" />
                      </head>
                      <body style="margin: 0;">
                        <div id="graphiql" style="height: 100vh;"></div>

                        <script
                          crossorigin
                          src="https://unpkg.com/react/umd/react.production.min.js"
                        ></script>
                        <script
                          crossorigin
                          src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"
                        ></script>
                        <script
                          crossorigin
                          src="https://unpkg.com/graphiql/graphiql.min.js"
                        ></script>

                        <script>
                          const fetcher = GraphiQL.createFetcher({
                            url: 'http://localhost:8080/api/graphql', 
                            enableIncrementalDelivery: false
                          });

                          ReactDOM.render(
                            React.createElement(GraphiQL, { fetcher: fetcher }),
                            document.getElementById('graphiql'),
                          );
                        </script>
                      </body>
                    </html>
                """.trimIndent().toByteArray(),
                        ContentType.parse("text/html"))
                }

                post("graphql") {
                    val request = call.receive<GraphQLJsonBody>()
                    call.respond(graphql(request))
                }
            }
        }
    }
}