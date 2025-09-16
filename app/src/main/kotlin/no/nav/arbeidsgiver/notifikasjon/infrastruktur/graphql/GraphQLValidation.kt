package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import graphql.GraphqlErrorException

private typealias Validator<T> = (T) -> T

object Validators {
    /**
     * https://nkom.no/telefoni-og-telefonnummer/telefonnummer-og-den-norske-nummerplan/alle-nummerserier-for-norske-telefonnumre
     */
    fun NorwegianMobilePhoneNumber(path: String): Validator<String> = { value ->
        val regex = Regex("""(\+47|0047)?[49]\d{7}""")
        if (!value.matches(regex)) {
            throw ValideringsFeil("$path: verdien er ikke et gyldig norsk mobilnummer.")
        }

        // blocked series ref NKOM E164 https://stenonicprdnoea01.blob.core.windows.net/enonicpubliccontainer/numsys/nkom.no/E164.csv
        val blockedSeries = listOf(
            42_00_00_00..42_99_99_99,
            43_00_00_00..43_99_99_99,
            44_00_00_00..44_99_99_99,
            45_36_00_00..45_36_99_99,
            49_00_00_00..49_99_99_99,
        )

        val tlf = value.replace(Regex("""(\+47|0047)"""), "").toIntOrNull()
        if (tlf in blockedSeries.flatten()) {
            throw ValideringsFeil("$path: verdien er ikke et gyldig norsk mobilnummer. Nummerserien ${tlf.toString().take(4)}xxxx er blokkert. (se: NKOM E164).")
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

    fun NotBlank(path: String): Validator<String> = { value ->
        if (value.isBlank()) {
            throw ValideringsFeil("$path: verdien kan ikke være blank.")
        }
        value
    }

    fun Email(path: String): Validator<String> = { value ->
        // Support EAI (Email Address Internationalization) including Norwegian characters æøå
        // \p{L} = Unicode letter, \p{N} = Unicode number
        val regex = Regex("""^[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.\p{L}{2,}$""", RegexOption.IGNORE_CASE)
        if (!value.matches(regex)) {
            throw ValideringsFeil("$path: verdien er ikke en gyldig e-postadresse.")
        }
        value
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
