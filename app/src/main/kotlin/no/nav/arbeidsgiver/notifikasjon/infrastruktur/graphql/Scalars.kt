package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import graphql.language.StringValue
import graphql.schema.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.tid.atOsloAsOffsetDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalQuery


inline fun <reified T: TemporalAccessor> dateTimeScalar(
    name: String,
    dateTimeFormatter: DateTimeFormatter,
    temporalQuery: TemporalQuery<T>,
): GraphQLScalarType = toFromStringScalar(
    name = name,
    parser = {  dateTimeFormatter.parse(it, temporalQuery) },
    printer = { dateTimeFormatter.format(it) }
)

inline fun <reified T: Any> toFromStringScalar(
    name: String,
    crossinline parser: (value: String) -> T,
    crossinline printer: (T) -> String = { it.toString() },
): GraphQLScalarType =
    GraphQLScalarType.newScalar()
        .name(name)
        .coercing(object: Coercing<T, String> {
            override fun serialize(obj: Any): String {
                if (obj is T) {
                    return printer(obj)
                } else {
                    throw CoercingSerializeException("unsupported value for $name coersion: $obj")
                }
            }

            override fun parseLiteral(input: Any): T =
                if (input is StringValue)
                    try {
                        parser(input.value)
                    } catch (e: RuntimeException) {
                        throw CoercingParseLiteralException(e.message, e)
                    }
                else
                    throw CoercingParseLiteralException("must be string")

            override fun parseValue(input: Any): T =
                if (input is String)
                    try {
                        parser(input)
                    } catch (e: RuntimeException) {
                        throw CoercingParseValueException(e.message, e)
                    }
                else
                    throw CoercingParseValueException("must be string")

        })
        .build()

object Scalars {
    val ISO8601DateTime: GraphQLScalarType =
        dateTimeScalar(
            name = "ISO8601DateTime",
            dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME,
            temporalQuery = {
                if (it.isSupported(ChronoField.OFFSET_SECONDS)) {
                    OffsetDateTime.from(it).withOffsetSameInstant(ZoneOffset.UTC)
                } else {
                    LocalDateTime.from(it).atOsloAsOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
                }
            }
        )

    val ISO8601LocalDateTime: GraphQLScalarType =
        dateTimeScalar(
            name = "ISO8601LocalDateTime",
            dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            temporalQuery = LocalDateTime::from
        )

    val ISO8601Date : GraphQLScalarType =
        dateTimeScalar(
            name = "ISO8601Date",
            dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE,
            temporalQuery = LocalDate::from
        )

    val ISO8601Duration: GraphQLScalarType = toFromStringScalar(
        name = "ISO8601Duration",
        parser = ISO8601Period::parse
    )
}