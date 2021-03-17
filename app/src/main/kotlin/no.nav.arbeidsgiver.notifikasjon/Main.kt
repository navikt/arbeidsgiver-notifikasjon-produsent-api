package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.JsonProcessingException
import graphql.ExecutionInput.newExecutionInput
import graphql.GraphQL.newGraphQL
import graphql.GraphQLError
import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates.coordinates
import graphql.schema.GraphQLArgument.newArgument
import graphql.schema.GraphQLCodeRegistry.newCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLObjectType.newObject
import graphql.schema.GraphQLSchema.newSchema
import io.ktor.application.*
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
import org.slf4j.event.Level
import java.util.*

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

fun objectType(typeName: String, setup: GraphQLObjectType.Builder.() -> Unit): GraphQLObjectType =
    newObject().apply{
        name(typeName)
        setup()
    }.build()

fun withField(fieldName: String, setup: GraphQLFieldDefinition.Builder.() -> Unit): GraphQLFieldDefinition =
    newFieldDefinition()
        .apply {
            name(fieldName)
            setup()
        }.build()

fun graphQLExecuter(): (request: GraphQLJsonBody) -> Any {
    val worldType = objectType("World") {
        description("A type for testing")
        field(withField("greeting") {
            type(GraphQLString)
        })
    }

    val worldFetcher = DataFetcher {
        World("Hello world!")
    }


    val additionType = objectType("Addition") {
        description("Addition table")
        field(newFieldDefinition()
            .name("sum")
            .type(GraphQLNonNull(GraphQLInt))
            .build()
        )
    }

    val additionFetcher = DataFetcher {
        val a = it.getArgument<Int>("a")
        val b = it.getArgument<Int>("b")
        Addition(a + b)
    }

    val queryType = newObject()
        .name("Query")
        .description("Root")
        .field(newFieldDefinition()
            .name("world")
            .type(worldType)
            .build())
        .field(newFieldDefinition()
            .name("addition")
            .argument(newArgument()
                .name("a")
                .type(GraphQLNonNull(GraphQLInt))
                .build())
            .argument(newArgument()
                .name("b")
                .type(GraphQLNonNull(GraphQLInt))
                .build())
            .type(additionType)
            .build())
        .build()


    val codeRegistry = newCodeRegistry()
        .dataFetcher(coordinates("Query", "world"), worldFetcher)
        .dataFetcher(coordinates("Query", "addition"), additionFetcher)
        .build()

    val schema = newSchema()
        .query(queryType)
        .codeRegistry(codeRegistry)
        .build()

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

    routing {
        route("internal") {
            get ("alive") {
                call.respond(HttpStatusCode.OK)
            }
            get ("ready") {
                call.respond(HttpStatusCode.OK)
            }

            get("metrics") {
                call.respond(meterRegistry.scrape())
            }
        }

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