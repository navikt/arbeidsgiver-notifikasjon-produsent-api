package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.module.kotlin.convertValue
import com.symbaloo.graphqlmicrometer.MicrometerInstrumentation
import graphql.*
import graphql.GraphQL.newGraphQL
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics.meterRegistry
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.coRecord
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.getTimer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import org.intellij.lang.annotations.Language

inline fun <reified T: Any?> DataFetchingEnvironment.getTypedArgument(name: String): T {
    val argument = this.getArgument<Any>(name) ?: throw RuntimeException("argument '$name' required, not provided")
    return laxObjectMapper.convertValue(argument)
}

inline fun <reified T: Any?> DataFetchingEnvironment.getTypedArgumentOrNull(name: String): T? {
    val value = this.getArgument<Any>(name) ?: return null
    return laxObjectMapper.convertValue(value)
}
inline fun <reified T: Any?> DataFetchingEnvironment.getTypedArgumentOrDefault(name: String, default: () -> T) =
    getTypedArgumentOrNull(name) ?: default()

fun RuntimeWiring.Builder.wire(typeName: String, config: TypeRuntimeWiring.Builder.() -> Unit) {
    this.type(typeName) {
        it.apply {
            config()
        }
    }
}

interface WithCoroutineScope {
    val coroutineScope: CoroutineScope
}

class UnhandledGraphQLExceptionError(
    exception: Exception,
    fieldName: String,
): GraphqlErrorException(
    newErrorException()
        .message(exception.message)
        .path(listOf(fieldName))
) {
    override val message: String = "unhandled exception ${exception.javaClass.canonicalName}: ${exception.message ?: ""}"
}

fun <T> TypeRuntimeWiring.Builder.coDataFetcher(
    fieldName: String,
    fetcher: suspend (DataFetchingEnvironment) -> T,
) {
    val timer = Timer.builder("graphql.datafetcher")
        .tag("fieldName", fieldName)
        .register(meterRegistry)
    dataFetcher(fieldName) { env ->
        val ctx = env.notifikasjonContext<WithCoroutineScope>()
        ctx.coroutineScope.future(Dispatchers.IO) {
            try {
                timer.coRecord {
                    fetcher(env)
                }
            } catch (e: GraphqlErrorException) {
                handleUnexpectedError(e, e)
            } catch (e: RuntimeException) {
                handleUnexpectedError(e, UnhandledGraphQLExceptionError(e, fieldName))
            }
        }
    }
}

fun handleUnexpectedError(exception: Exception, error: GraphQLError): DataFetcherResult<*> {
    GraphQLLogger.log.error(
        "unhandled exception while executing coDataFetcher: {}",
        exception.javaClass.canonicalName,
        exception
    )
    return DataFetcherResult.newResult<Nothing?>()
        .error(error)
        .build()
}

object GraphQLLogger {
    val log = logger()
}

fun jsonTypeName(clazz: Class<*>): String =
    clazz.getAnnotation(JsonTypeName::class.java).value

inline fun <reified T : Any> RuntimeWiring.Builder.resolveSubtypes() {
    GraphQLLogger.log.info("SubtypeResolver registered for ${T::class.simpleName}")
    type(T::class.simpleName) {
        it.typeResolver { env ->
            val obj = env.getObject<T>()
            val name = jsonTypeName(obj.javaClass)
            env.schema.getObjectType(name)
        }
    }
}

fun createGraphQL(
    schemaFilePath: String,
    runtimeWiringConfig: RuntimeWiring.Builder.() -> Unit
): GraphQL {
    val typeDefinitionRegistry = SchemaParser()
        .parse({}.javaClass.getResourceAsStream(schemaFilePath))

    val runtimeWiring = newRuntimeWiring()
        .apply(runtimeWiringConfig)
        .build()

    val schema = SchemaGenerator()
        .makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
    return newGraphQL(schema)
        .instrumentation(MicrometerInstrumentation(meterRegistry))
        .build()
}

data class GraphQLRequest(
    @Language("GraphQL") val query: String,
    val operationName: String? = null,
    val variables: Map<String, Any?>? = null,
) {
    // Regex to match the first word inside the mutation or query block
    val queryNameRegex = Regex("(mutation|query)\\b[\\s\\S]*?\\{\\s*(\\w+)", RegexOption.MULTILINE)
    val queryName: String
        get() {
            return queryNameRegex.find(query)?.groups?.get(2)?.value ?: "unknown"
        }
}

inline fun requireGraphql(check: Boolean, message: () -> String) {
    if (!check) {
        throw GraphqlErrorException
            .newErrorException()
            .message(message())
            .build()
    }
}

fun <T> DataFetchingEnvironment.notifikasjonContext(): T =
    this.graphQlContext["context"]

class TypedGraphQL<T : WithCoroutineScope>(
    private val graphQL: GraphQL
) {
    fun execute(request: GraphQLRequest, context: T): ExecutionResult {
        val executionInput = executionInput(request, context)
        return graphQL.execute(executionInput)
    }

    private fun executionInput(
        request: GraphQLRequest,
        context: T
    ): ExecutionInput {
        return ExecutionInput.newExecutionInput()
            .apply {
                query(request.query)

                request.operationName?.let {
                    operationName(it)
                }

                request.variables?.let {
                    variables(it)
                }

                graphQLContext(mapOf("context" to context))
            }.build()
    }
}

fun <T : WithCoroutineScope> TypedGraphQL<T>.timedExecute(
    request: GraphQLRequest,
    context: T
): ExecutionResult = with(Timer.start(meterRegistry)) {
    execute(request, context).also { result ->
        if (result.errors.isNotEmpty()) {
            GraphQLLogger.log.error("graphql request failed: {}", result.errors)
        }
        val tags = mutableSetOf(
            "queryName" to (request.queryName),
            "result" to if (result.errors.isEmpty()) "success" else "error"
        )
        if (context is ProdusentAPI.Context) tags.add("produsent" to context.appName)
        stop(getTimer("graphql.timer", tags, "graphql request"))
    }
}
