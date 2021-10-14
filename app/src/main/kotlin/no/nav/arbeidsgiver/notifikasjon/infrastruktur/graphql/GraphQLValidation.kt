package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

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
        val validator = argument.createValidator()
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

    private fun GraphQLArgument.createValidator(): Validator? {
        val path = listOf("argument '${this.name}'")
        val argumentValidator: Validator? = this.directives
            .filter { it.name != "Validate" }
            .map { directive ->
            VALIDATORS[directive.name]
                ?.createValidator(path, directive, this.type)
                ?: throw Error("Unknown validation directive ${directive.name}")
        }
            .andAll()

        val nestedValidators = this.type.createValidator(path)

        return argumentValidator and nestedValidators
    }

    private fun GraphQLInputType.createValidator(path: Path): Validator? =
        when (this) {
            is GraphQLNonNull -> this.createValidator(path)
            is GraphQLInputObjectType -> this.createValidator(path)
            is GraphQLScalarType -> null
            is GraphQLList -> this.createValidator(path)
            else -> throw Error("Unexpected graphql type ${this.javaClass.canonicalName} in ${path.joinToString(", ")}")
        }

    private fun GraphQLList.createValidator(path: Path): Validator? =
        (this.wrappedType as GraphQLInputType)
            .createValidator(path + listOf("array element"))
            ?.let { validate ->
                { list ->
                    list as List<Any?>
                    for (value in list) {
                        validate(value)
                    }
                }
            }


    private fun GraphQLNonNull.createValidator(path: Path): Validator? =
        (this.wrappedType as GraphQLInputType).createValidator(path)
            ?.let { validate ->
                { value ->
                    if (value != null) {
                        validate(value)
                    }
                }
            }

    private fun GraphQLInputObjectType.createValidator(path: Path): Validator? {
        val extendedPath = path + listOf("object type '${this.name}'")
        val objectValidators = this.directives.map { directive ->
            VALIDATORS[directive.name]
                ?.createValidator(extendedPath, directive, this)
                ?: throw Error("Unknown directive '${directive.name}' to validate")
        }
        val fieldValidators = this.fields.mapNotNull { it.createValidator(path) }

        return (fieldValidators + objectValidators).andAll()
    }

    private fun GraphQLInputObjectField.createValidator(path: Path): Validator? {
        val extendedPath = path + listOf("field '${this.name}'")
        val validators = this.directives.map { directive ->
            VALIDATORS[directive.name]
                ?.createValidator(extendedPath, directive, this.type)
                ?: throw Error("Unknown directive '${directive.name}' to validate")
        }

        val otherValidators = type.createValidator(extendedPath)

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
}

private typealias Validator = (Any?) -> Unit

private typealias Path = List<String>
private fun Path.asString() = this.joinToString(", ")

private infix fun Validator?.and(other: Validator?): Validator? =
    if (this == null && other == null)
        null
    else
        { value ->
            if (this != null) {
                this(value)
            }
            if (other != null) {
                other(value)
            }
        }

private fun List<Validator?>?.andAll(): Validator? =
    this.orEmpty()
        .fold(initial = null, operation = Validator?::and)

interface ValueValidator {
    val name: String
    fun createValidator(path: List<String>, directive: GraphQLDirective, type: GraphQLType): Validator
}

private val VALIDATORS = listOf(
    object : ValueValidator {
        override val name = "MaxValue"

        override fun createValidator(path: Path, directive: GraphQLDirective, type: GraphQLType): Validator {
            val upToIncluding = directive.getArgument("upToIncluding").value as Int
            return { value ->
                val valueInt = value as Int?
                if (valueInt != null && valueInt > upToIncluding) {
                    throw ValideringsFeil("${path.asString()}: verdien overstiger maks antall tegn: verdi=${valueInt}, upToIncluding=${upToIncluding}.")
                }
            }
        }
    },
    object : ValueValidator {
        override val name = "MaxLength"

        override fun createValidator(path: Path, directive: GraphQLDirective, type: GraphQLType): Validator {
            val max = directive.getArgument("max").value as Int
            return { value ->
                val valueStr = value as String?
                if (valueStr != null && valueStr.length > max) {
                    throw ValideringsFeil("${path.asString()}: verdien overstiger maks antall tegn, antall=${valueStr.length}, maks=$max.")
                }
            }
        }
    },
    object : ValueValidator {
        override val name = "NonIdentifying"

        override fun createValidator(path: Path, directive: GraphQLDirective, type: GraphQLType): Validator {
            return { value ->
                val valueStr = value as String?
                if (valueStr != null && valueStr.contains(Regex("""\d{11}"""))) {
                    throw ValideringsFeil("${path.asString()}: verdien inneholder uønsket data: personnummer (11 siffer)")
                }
            }
        }
    },
    object : ValueValidator {
        override val name = "ExactlyOneFieldGiven"

        override fun createValidator(path: Path, directive: GraphQLDirective, type: GraphQLType): Validator {
            type as GraphQLInputObjectType
            val fieldNames = type.fields.map { it.name }.toSet()
            return { value ->
                value as Map<String, Any?>
                val fieldsGiven = value.filter {
                    fieldNames.contains(it.key) && it.value != null
                }
                when (fieldsGiven.size) {
                    1 -> Unit
                    0 -> throw ValideringsFeil("${path.asString()}: nøyaktig ett felt skal være satt. (Ingen felt er satt)")
                    else -> throw ValideringsFeil("${path.asString()}: nøyaktig ett felt skal være satt. (${fieldsGiven.keys.joinToString(", ")} er gitt)")
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
