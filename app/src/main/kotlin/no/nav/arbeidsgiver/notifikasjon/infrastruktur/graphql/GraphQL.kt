package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.module.kotlin.convertValue
import graphql.*
import graphql.GraphQL.newGraphQL
import graphql.execution.DataFetcherResult
import graphql.language.SourceLocation
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.intellij.lang.annotations.Language

inline fun <reified T> DataFetchingEnvironment.getTypedArgument(name: String): T =
    laxObjectMapper.convertValue(this.getArgument(name))

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
    private val exception: Exception
): GraphQLError {
    override fun getErrorType(): ErrorClassification {
        return ErrorType.DataFetchingException
    }

    override fun getLocations(): MutableList<SourceLocation> {
        return mutableListOf()
    }

    override fun getMessage(): String {
        return "unhandled exception ${exception.javaClass.canonicalName}: ${exception.message ?: ""}"
    }
}

fun <T> TypeRuntimeWiring.Builder.coDataFetcher(
    fieldName: String,
    fetcher: suspend (DataFetchingEnvironment) -> T,
) {
    dataFetcher(fieldName) { env ->
        val ctx = env.getContext<WithCoroutineScope>()
        ctx.coroutineScope.future(Dispatchers.IO) {
            try {
                fetcher(env)
            } catch (e: GraphqlErrorException) {
                handleUnexepctedError(e, e)
            } catch (e: Exception) {
                handleUnexepctedError(e, UnhandledGraphQLExceptionError(e))
            }
        }
    }
}

fun handleUnexepctedError(exception: Exception, error: GraphQLError): DataFetcherResult<*> {
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
    return newGraphQL(schema).build()
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

class TypedGraphQL<T : WithCoroutineScope>(
    private val graphQL: GraphQL
) {
    fun execute(request: GraphQLRequest, context: T): Any {
        val executionInput = executionInput(request, context)
        return graphQL.execute(executionInput).toSpecification()
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

                context(context)
            }.build()
    }
}
