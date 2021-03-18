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
import java.util.*


/* Hello world */
data class World(val greeting: String)

val queryWorld = DataFetcher<World> {
    World("Hello world!")
}


/* Addition */
data class Addition(val sum: Int)

val queryAddition = DataFetcher<Addition> {
    val a = it.getArgument<Int>("a")
    val b = it.getArgument<Int>("b")
    Addition(a + b)
}

/* Counter */
data class Counter(val count: Int)

var counter = 0
val mutationIncrement = DataFetcher<Counter> {
    counter +=1
    Counter(counter)
}


/* Beskjeder */
data class Beskjed(val id: String, val tekst: String)
data class InputBeskjed(val tekst: String)

val beskjedRepo = mutableMapOf<String, Beskjed>()

val mutationNyBeskjed = DataFetcher<Beskjed> {
    val nyBeskjed= it.getTypedArgument<InputBeskjed>("nyBeskjed")
    val id = UUID.randomUUID().toString()
    val b = Beskjed( id,nyBeskjed.tekst)
    beskjedRepo[id] = b
    b
}

val queryBeskjeder = DataFetcher<List<Beskjed>> {
    beskjedRepo.values.toList()
}

val queryBeskjed = DataFetcher {
    val id = it.getArgument<String>("id")
    beskjedRepo[id]
}


/* Infrastructure/configuration etc. */

private inline fun <reified T> DataFetchingEnvironment.getTypedArgument(name:String): T {
    return objectMapper.convertValue(this.getArgument(name))
}

private fun GraphQLCodeRegistry.Builder.dataFetcher(
    parentType: String,
    fieldName: String,
    dataFetcher: DataFetcher<*>
): GraphQLCodeRegistry.Builder {
    return this.dataFetcher(FieldCoordinates.coordinates(parentType, fieldName), dataFetcher)
}

fun createGraphQL(): GraphQL {
    val codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        .dataFetcher("Query", "world", queryWorld)
        .dataFetcher("Query", "addition", queryAddition)
        .dataFetcher("Mutation", "increment", mutationIncrement)
        .dataFetcher("Mutation", "nyBeskjed", mutationNyBeskjed)
        .dataFetcher("Query", "beskjeder", queryBeskjeder)
        .dataFetcher("Query", "beskjed", queryBeskjed)
        .build()

    val typeDefinitionRegistry = SchemaParser().parse({}.javaClass.getResourceAsStream("/schema.graphqls"))
    val runtimeWiring = newRuntimeWiring().codeRegistry(codeRegistry).build()
    val schema = SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
    return newGraphQL(schema).build()
}

data class GraphQLRequest(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, String>? = null
)

fun GraphQL.execute(request: GraphQLRequest): Any {
    val executionInput = ExecutionInput.newExecutionInput()
        .apply {
            query(request.query)

            request.operationName?.let {
                operationName(it)
            }

            request.variables?.let {
                variables(it)
            }
        }.build()

    return this.execute(executionInput).toSpecification()
}
