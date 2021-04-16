package no.nav.arbeidsgiver.notifikasjon.graphql

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object Scalars {
    val ISO8601DateTime: GraphQLScalarType = GraphQLScalarType.newScalar()
        .name("ISO8601DateTime")
        .coercing(object : Coercing<OffsetDateTime, String> {
            override fun serialize(result: Any?): String? {
                if (result is OffsetDateTime) {
                    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(result)
                } else {
                    throw CoercingSerializeException("unsupported value for ISO8601DateTime coersion: $result")
                }
            }

            override fun parseValue(input: Any?): OffsetDateTime? {
                return if (input == null) null else OffsetDateTime.parse(input as String)

            }

            override fun parseLiteral(input: Any?): OffsetDateTime? {
                return parseValue((input as StringValue).value)
            }
        }).build()
}