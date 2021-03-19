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
import org.apache.kafka.clients.producer.Producer
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("graphql")!!

data class FnrmottakerInput (
    val fodselsnummer: String,
    val virksomhetsnummer: String
)

data class AltinnmottakerInput(
    val altinntjenesteKode: String,
    val altinntjenesteVersjon: String,
    val virksomhetsnummer: String,
)

data class MottakerInput(
    val altinn: AltinnmottakerInput?,
    val fnr: FnrmottakerInput?
)

data class BeskjedInput(
    val merkelapp: String,
    val tekst: String,
    val grupperingsid: String?,
    val lenke: String,
    val eksternid: String?,
    val mottaker: MottakerInput,
    val opprettetTidspunkt: String?
)

data class BeskjedResultat(
    val id: String
)

private fun nyBeskjedMutation(kafkaProducer: Producer<Key, Value>) = DataFetcher {
    val nyBeskjed = it.getTypedArgument<BeskjedInput>("nyBeskjed")
    val id = UUID.randomUUID().toString()
    log.info("mottatt ny beskjed, id: $id, beskjed: $nyBeskjed")
    kafkaProducer.sendEvent(Key(id), Value(nyBeskjed.tekst))
    BeskjedResultat(id)
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

fun createGraphQL(
    kafkaProducer: Producer<Key, Value> = createProducer()
): GraphQL {
    val codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        .dataFetcher("Mutation", "nyBeskjed", nyBeskjedMutation(kafkaProducer))
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
