package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.module.kotlin.convertValue
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQL.newGraphQL
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

inline fun <reified T> DataFetchingEnvironment.getTypedArgument(name:String): T =
    objectMapper.convertValue(this.getArgument(name))

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

fun <T> TypeRuntimeWiring.Builder.coDataFetcher(fieldName: String, fetcher: suspend (DataFetchingEnvironment) -> T) {
    dataFetcher(fieldName) { env ->
        val ctx = env.getContext<WithCoroutineScope>()
        ctx.coroutineScope.future(Dispatchers.Default) {
            fetcher(env)
        }
    }
}

inline fun <reified T : Any> RuntimeWiring.Builder.resolveSubtypes() {
    type(T::class.simpleName) {
        it.typeResolver { env ->
            env.schema.getObjectType(env.getObject<T>().javaClass.simpleName)
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
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, String>? = null,
)

class TypedGraphQL<T: WithCoroutineScope>(
    private val graphQL: GraphQL
) {
    fun execute(request: GraphQLRequest, context: T): Any {
        val executionInput = executionInput(request, context)
        return graphQL.execute(executionInput).toSpecification()
    }

    suspend fun executeAsync(request: GraphQLRequest, context: T): Any {
        val executionInput = executionInput(request, context)
        return graphQL.executeAsync(executionInput).await().toSpecification()
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
