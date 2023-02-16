package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.module.kotlin.convertValue
import com.symbaloo.graphqlmicrometer.MicrometerInstrumentation
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQL.newGraphQL
import graphql.GraphQLError
import graphql.GraphqlErrorException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics.meterRegistry
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.intellij.lang.annotations.Language

inline fun <reified T: Any?> DataFetchingEnvironment.getTypedArgument(name: String): T =
    laxObjectMapper.convertValue(this.getArgument(name))

inline fun <reified T: Any?> DataFetchingEnvironment.getTypedArgumentOrNull(name: String): T? {
    val value = this.getArgument<Any>(name) ?: return null
    return laxObjectMapper.convertValue(value)
}

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
    dataFetcher(fieldName) { env ->
        val ctx = env.notifikasjonContext<WithCoroutineScope>()
        ctx.coroutineScope.future(Dispatchers.IO) {
            try {
                fetcher(env)
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
)

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
    fun execute(request: GraphQLRequest, context: T): Any {
        val executionInput = executionInput(request, context)
        val result = graphQL.execute(executionInput)
        return result.toSpecification()
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
