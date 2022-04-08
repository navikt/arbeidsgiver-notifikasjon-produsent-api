package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import graphql.language.StringValue
import graphql.schema.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalQuery


inline fun <reified T: TemporalAccessor> dateTimeScalar(
    name: String,
    dateTimeFormatter: DateTimeFormatter,
    temporalQuery: TemporalQuery<T>,
): GraphQLScalarType =
    GraphQLScalarType.newScalar()
        .name(name)
        .coercing(object: Coercing<T, String> {
            override fun serialize(obj: Any): String {
                if (obj is T) {
                    return dateTimeFormatter.format(obj)
                } else {
                    throw CoercingSerializeException("unsupported value for $name coersion: $obj")
                }
            }

            override fun parseLiteral(input: Any): T =
                if (input is StringValue)
                    try {
                        dateTimeFormatter.parse(input.value, temporalQuery)
                    } catch (e: DateTimeParseException) {
                        throw CoercingParseLiteralException(e)
                    }
                else
                    throw CoercingParseLiteralException("must be string")

            override fun parseValue(input: Any): T =
                if (input is String)
                    try {
                        dateTimeFormatter.parse(input, temporalQuery)
                    } catch (e: DateTimeParseException) {
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

    val ISO8601Duration: GraphQLScalarType = GraphQLScalarType.newScalar()
        .name("ISO8601Duration")
        .coercing(object : Coercing<Duration?, String> {
            override fun serialize(obj: Any): String {
                if (obj is Duration) {
                    return obj.toString()
                } else {
                    throw CoercingSerializeException("unsupported value for ISO8601Duration coersion: $obj")
                }
            }

            override fun parseLiteral(input: Any): Duration? =
                if (input is StringValue)
                    try {
                        Duration.parse(input.value)
                    } catch (e: DateTimeParseException) {
                        throw CoercingParseLiteralException(e)
                    }
                else
                    throw CoercingParseLiteralException("must be string")

            override fun parseValue(input: Any): Duration? =
                if (input is String)
                    try {
                        Duration.parse(input)
                    } catch (e: DateTimeParseException) {
                        throw CoercingParseValueException(e)
                    }
                else
                    throw CoercingParseValueException("must be string")

        })
        .build()
}