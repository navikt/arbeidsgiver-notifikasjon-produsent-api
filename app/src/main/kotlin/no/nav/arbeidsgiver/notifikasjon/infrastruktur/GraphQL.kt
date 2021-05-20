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


private typealias Validator = (Any?) -> Unit

object ValidateDirective : SchemaDirectiveWiring {

    override fun onArgument(environment: SchemaDirectiveWiringEnvironment<GraphQLArgument>): GraphQLArgument {
        val argument = environment.element
        val validator = argument.type.createValidator()
            ?: return argument
        val dataFetcher = environment.codeRegistry.getDataFetcher(
            environment.fieldsContainer,
            environment.fieldDefinition
        )

        environment.codeRegistry.dataFetcher(environment.fieldsContainer, environment.fieldDefinition) {
            val value = it.getArgument<Any?>(argument.name)
            validator(value)
            dataFetcher.get(it)
        }

        return argument
    }

    private fun GraphQLInputType.createValidator(): Validator? {
        when (this) {
            is GraphQLNonNull -> {
                /* Is this guaranteed to be true? */
                return (this.wrappedType as GraphQLInputType).createValidator()
                    ?.let { validate ->
                        { value ->
                            if (value != null) {
                                validate(value)
                            }
                        }
                    }

            }
            is GraphQLInputObjectType -> {
                val validators = this.fields.mapNotNull { it.createValidator() }
                if (validators.isEmpty()) {
                    return null
                } else {
                    return { value ->
                        validators.forEach { validator ->
                            validator(value)
                        }
                    }
                }
            }
            is GraphQLScalarType -> {
                return null
            }

            /* Other types to support here? */

            else -> {
                throw Error("Unexpected graphql type ${this.javaClass.canonicalName}")
            }
        }
    }

    fun GraphQLInputObjectField.createValidator(): Validator? {
        val validators = this.directives.mapNotNull {
            it.createValidator(this)
        }

        val otherValidators = type.createValidator()

        if (validators.isEmpty() && otherValidators == null) {
            return null
        } else {
            return { objectValue ->
                val fieldValue = (objectValue as Map<String, Any?>)[name]
                validators.forEach { validator ->
                    validator(fieldValue)
                }

                otherValidators?.let { it(fieldValue) }
            }
        }
    }

    private val validators = listOf(MaxLengthValidator)
        .associateBy { it.name }

    private fun GraphQLDirective.createValidator(field: GraphQLInputObjectField): Validator =
        validators[name]
            ?.createValidator(this, field)
            ?: throw Error("Unknown directive '$name' to validate")
}

interface ValidatorBuilder {
    val name: String
    fun createValidator(directive: GraphQLDirective, field: GraphQLInputObjectField): Validator
}

object MaxLengthValidator : ValidatorBuilder {
    override val name = "MaxLength"

    override fun createValidator(directive: GraphQLDirective, field: GraphQLInputObjectField): Validator {
        val max = directive.getArgument("max").value as Int
        return { value ->
            val valueStr = value as String?
            if (valueStr != null && valueStr.length > max) {
                throw ValideringsFeil("verdi på felt '${field.name}' overstiger max antall tegn. antall=${valueStr.length}, max=$max")
            }
        }
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
