package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLError
import graphql.language.SourceLocation
import graphql.schema.*
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment

object ValidateDirective : SchemaDirectiveWiring {

    /* Find all validation annotations for this argument. */
    override fun onArgument(environment: SchemaDirectiveWiringEnvironment<GraphQLArgument>): GraphQLArgument {
        val argument = environment.element
        val validator = argument.type.createValidator(argument.directives)
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

    private fun GraphQLInputType.createValidator(directives: List<GraphQLDirective> = emptyList()): Validator? =
        when (this) {
            is GraphQLNonNull -> this.createValidator()
            is GraphQLInputObjectType -> this.createValidator()
            is GraphQLScalarType -> this.createValidator(directives)
            else -> throw Error("Unexpected graphql type ${this.javaClass.canonicalName}")
        }

    private fun GraphQLNonNull.createValidator(): Validator? =
        (this.wrappedType as GraphQLInputType).createValidator()
            ?.let { validate ->
                { value ->
                    if (value != null) {
                        validate(value)
                    }
                }
            }

    private fun GraphQLInputObjectType.createValidator(): Validator? {
        val objectValidators = this.directives.map { directive ->
            OBJECT_VALIDATORS[directive.name]
                ?.createValidator(directive, this)
                ?: throw Error("Unknown directive '${directive.name}' to validate")
        }
        val fieldValidators = this.fields.mapNotNull { it.createValidator() }

        if (objectValidators.isEmpty() && fieldValidators.isEmpty()) {
            return null
        } else {
            return { value ->
                for (validator in objectValidators) {
                    validator(value)
                }
                for (validator in fieldValidators) {
                    validator(value)
                }
            }
        }
    }

    private fun GraphQLInputObjectField.createValidator(): Validator? {
        val validators = this.directives.map { directive ->
            FIELD_VALIDATORS[directive.name]
                ?.createValidator(directive, this)
                ?: throw Error("Unknown directive '${directive.name}' to validate")
        }

        val otherValidators = type.createValidator()

        if (validators.isEmpty() && otherValidators == null) {
            return null
        } else {
            return { objectValue ->
                objectValue as Map<String, Any?>
                val fieldValue = objectValue[name]
                validators.forEach { validator ->
                    validator(fieldValue)
                }
                otherValidators?.let { it(fieldValue) }
            }
        }
    }

    private fun GraphQLScalarType.createValidator(directives: List<GraphQLDirective>): Validator? {
        val validators = directives.filterNot { it.name == "Validate" }.map { directive ->
            SCALAR_VALIDATORS[directive.name]
                ?.createValidator(directive, this)
                ?: throw Error("Unknown directive '${directive.name}' to validate")
        }

        if (validators.isEmpty()) {
            return null
        } else {
            return { value ->
                for (validator in validators) {
                    validator(value)
                }
            }
        }
    }
}

private typealias Validator = (Any?) -> Unit

interface ValidatorBuilder<T> {
    val name: String
    fun createValidator(directive: GraphQLDirective, obj: T): Validator
}

private val FIELD_VALIDATORS = listOf(
    object : ValidatorBuilder<GraphQLInputObjectField> {
        override val name = "MaxLength"

        override fun createValidator(directive: GraphQLDirective, obj: GraphQLInputObjectField): Validator {
            val max = directive.getArgument("max").value as Int
            return { value ->
                val valueStr = value as String?
                if (valueStr != null && valueStr.length > max) {
                    throw ValideringsFeil("verdi på felt '${obj.name}' overstiger max antall tegn. antall=${valueStr.length}, max=$max")
                }
            }
        }
    },
    object : ValidatorBuilder<GraphQLInputObjectField> {
        override val name = "NonIdentifying"

        override fun createValidator(directive: GraphQLDirective, obj: GraphQLInputObjectField): Validator {
            return { value ->
                val valueStr = value as String?
                if (valueStr != null && valueStr.contains(Regex("""\d{11}"""))) {
                    throw ValideringsFeil("felt '${obj.name}' kan ikke inneholde identifiserende data")
                }
            }
        }
    }
).associateBy { it.name }

private val OBJECT_VALIDATORS = listOf<ValidatorBuilder<GraphQLInputObjectType>>(
    object : ValidatorBuilder<GraphQLInputObjectType> {
        override val name = "ExactlyOneFieldGiven"

        override fun createValidator(directive: GraphQLDirective, obj: GraphQLInputObjectType): Validator {
            val fieldNames = obj.fields.map { it.name }.toSet()
            return { value ->
                value as Map<*, *>
                val fieldsGiven = value.filter {
                    fieldNames.contains(it.key) && it.value != null
                }
                when (fieldsGiven.size) {
                    1 -> Unit
                    0 -> throw ValideringsFeil("Nøyaktig ett felt skal være satt. (Ingen felt er satt)")
                    else -> throw ValideringsFeil("Nøyaktig ett felt skal være satt. (${fieldsGiven.keys.joinToString(", ")} er gitt)")
                }
            }
        }
    }
).associateBy { it.name }
private val SCALAR_VALIDATORS = listOf<ValidatorBuilder<GraphQLScalarType>>(
    object : ValidatorBuilder<GraphQLScalarType> {
        override val name = "MaxSize"

        override fun createValidator(directive: GraphQLDirective, obj: GraphQLScalarType): Validator {
            val max = directive.getArgument("max").value as Int
            return { value ->
                val intValue = value as Int?
                if (intValue != null && intValue > max) {
                    throw ValideringsFeil("verdi på felt '${obj.name}' overstiger max. verdi=${intValue}, max=$max")
                }
            }
        }
    }
).associateBy { it.name }


/**
 * lånt fra https://github.com/graphql-java/graphql-java/issues/1022#issuecomment-723369519
 * workaround for mismatch mellom kotlin og hvordan graphql eksponerer custom feil
 */
class ValideringsFeil(@JvmField override val message: String) : RuntimeException(message), GraphQLError {
    override fun getMessage(): String? = super.message
    override fun getLocations(): MutableList<SourceLocation> = mutableListOf()
    override fun getErrorType(): ErrorClassification = ErrorType.DataFetchingException
}
