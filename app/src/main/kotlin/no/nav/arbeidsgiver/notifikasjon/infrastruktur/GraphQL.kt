package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.module.kotlin.convertValue
import graphql.*
import graphql.GraphQL.newGraphQL
import graphql.language.SourceLocation
import graphql.schema.*
import graphql.schema.idl.*
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

inline fun <reified T> DataFetchingEnvironment.getTypedArgument(name: String): T =
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

class TypedGraphQL<T : WithCoroutineScope>(
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


/**
 * TODO:
 * antagelse om GraphQLInputObjectType vil brekke ved f.eks scalar eller primitive, osv
 * Nullable typer og nested verdier er hbeller ikke støttet P.T.
 */
object ValidateDirective : SchemaDirectiveWiring {
    override fun onArgument(environment: SchemaDirectiveWiringEnvironment<GraphQLArgument>): GraphQLArgument {
        val dataFetcher =
            environment.codeRegistry.getDataFetcher(
                environment.fieldsContainer,
                environment.fieldDefinition
            )

        val directivesPerFelt: Map<String, List<GraphQLDirective>> =
            ((environment.element.type as GraphQLNonNull).originalWrappedType as GraphQLInputObjectType).fields.filter {
                it.directives.isNotEmpty()
            }.associate {
                it.name to it.directives
            }

        environment.codeRegistry.dataFetcher(environment.fieldsContainer, environment.fieldDefinition) {
            val arg = it.getArgument<Map<String, String>>(environment.element.name)
            directivesPerFelt.forEach { entry ->
                val (feltNavn, directives) = entry
                val feltVerdi = arg[feltNavn]
                directives.forEach { directive ->
                    when (directive.name) {
                        "MaxLength" -> {
                            val max = directive.getArgument("max").value as Int
                            if (feltVerdi != null && feltVerdi.length > max) {
                                throw ValideringsFeil("verdi på felt '$feltNavn' overstiger max antall tegn. antall=${feltVerdi.length}, max=$max")
                            }
                        }
                        else ->
                            throw Error("ukjent directive ${directive.name}")
                    }
                }
            }
            dataFetcher.get(it)
        }
        return environment.element
    }
}

/**
 * lånt fra https://github.com/graphql-java/graphql-java/issues/1022#issuecomment-723369519
 * workaround for mismatch mellom kotlin og hvordan graphql eksponerer custom feil
 */
class ValideringsFeil(@JvmField override val message: String) : RuntimeException(message), GraphQLError {
    override fun getMessage(): String? = super.message
    override fun getLocations(): MutableList<SourceLocation> = mutableListOf()
    override fun getErrorType(): ErrorClassification = ErrorType.DataFetchingException
}
