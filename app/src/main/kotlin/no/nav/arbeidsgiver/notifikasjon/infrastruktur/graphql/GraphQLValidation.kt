package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import graphql.GraphqlErrorException

private typealias Validator<T> = (T) -> T

object Validators {
    fun NorwegianMobilePhoneNumber(path: String): Validator<String?> = { value ->
        val regex = Regex("""(\+47|0047)?[49]\d{7}""")
        if (value != null && !value.matches(regex)) {
            throw ValideringsFeil("$path: verdien er ikke et gyldig norsk mobilnummer.")
        }
        value
    }

    fun NonIdentifying(path: String): Validator<String> = { value ->
        val regex = Regex("""\d{11}""")
        if (value.contains(regex)) {
            throw ValideringsFeil("$path: verdien inneholder uønsket data: personnummer (11 siffer)")
        }
        value
    }

    fun MaxValue(path: String, upToIncluding: Int): Validator<Int> = { value ->
        if (value > upToIncluding) {
            throw ValideringsFeil("$path: verdien overstiger maks antall tegn: verdi=${value}, upToIncluding=${upToIncluding}.")
        }
        value
    }

    fun MaxLength(path: String, max: Int): Validator<String> = { value ->
        if (value.length > max) {
            throw ValideringsFeil("$path: verdien overstiger maks antall tegn, antall=${value.length}, maks=$max.")
        }
        value
    }

    fun ExactlyOneFieldGiven(path: String): Validator<Map<String, Any?>> = { value ->
        val fieldsGiven = value.filterValues {
            it != null
        }
        when (fieldsGiven.size) {
            1 -> value
            0 -> throw ValideringsFeil("$path: nøyaktig ett felt skal være satt. (Ingen felt er satt)")
            else -> throw ValideringsFeil("$path: nøyaktig ett felt skal være satt. (${fieldsGiven.keys.joinToString(", ")} er gitt)")
        }
    }

    fun <T> compose(
        vararg validators: Validator<T>
    ): Validator<T> = { value ->
        for (validator in validators) {
            validator(value)
        }
        value
    }
}

class ValideringsFeil(message: String) : GraphqlErrorException(newErrorException().message(message))
