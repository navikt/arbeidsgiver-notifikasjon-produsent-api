package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import graphql.language.StringValue
import graphql.schema.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
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
                    } catch (e: Exception) {
                        throw CoercingParseLiteralException(e)
                    }
                else
                    throw CoercingParseLiteralException("must be string")

            override fun parseValue(input: Any): T =
                if (input is String)
                    try {
                        parser(input)
                    } catch (e: Exception) {
                        throw CoercingParseValueException(e)
                    }
                else
                    throw CoercingParseValueException("must be string")

        })
        .build()

object Scalars {
    val ISO8601DateTime: GraphQLScalarType =
        dateTimeScalar(
            name = "ISO8601DateTime",
            dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            temporalQuery = OffsetDateTime::from
        )

    val ISO8601LocalDateTime: GraphQLScalarType =
        dateTimeScalar(
            name = "ISO8601LocalDateTime",
            dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            temporalQuery = LocalDateTime::from
        )

    val ISO8601Duration: GraphQLScalarType = toFromStringScalar(
        name = "ISO8601Duration",
        parser = Duration::parse
    )
}