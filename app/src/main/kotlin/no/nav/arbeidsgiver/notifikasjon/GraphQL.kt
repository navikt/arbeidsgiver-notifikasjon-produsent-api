package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.convertValue
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQL.newGraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser

/* Infrastructure/configuration etc. */

inline fun <reified T> DataFetchingEnvironment.getTypedArgument(name:String): T {
    return objectMapper.convertValue(this.getArgument(name))
}

fun GraphQLCodeRegistry.Builder.dataFetcher(
    parentType: String,
    fieldName: String,
    dataFetcher: DataFetcher<*>
): GraphQLCodeRegistry.Builder {
    return this.dataFetcher(FieldCoordinates.coordinates(parentType, fieldName), dataFetcher)
}


typealias CodeRegistryConfig = GraphQLCodeRegistry.Builder.() -> Unit

fun createGraphQL(
    schemaFilePath: String,
    codeRegistryConfig: CodeRegistryConfig
): GraphQL {
    val codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        .apply(codeRegistryConfig)
        .build()

    val typeDefinitionRegistry = SchemaParser().parse({}.javaClass.getResourceAsStream(schemaFilePath))
    val runtimeWiring = newRuntimeWiring().codeRegistry(codeRegistry).build()
    val schema = SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
    return newGraphQL(schema).build()
}

data class GraphQLRequest(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, String>? = null
)

data class Context(
    val produsentId: String
)

fun GraphQL.execute(request: GraphQLRequest, context: Context? = null): Any {
    val executionInput = ExecutionInput.newExecutionInput()
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

    return this.execute(executionInput).toSpecification()
}
