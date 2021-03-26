package no.nav.arbeidsgiver.notifikasjon.graphql

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import java.time.Instant
import java.time.format.DateTimeFormatter

object Scalars {
    val Instant: GraphQLScalarType = GraphQLScalarType.newScalar()
        .name("Instant")
        .coercing(object : Coercing<Instant, String> {
            override fun serialize(result: Any?): String? {
                if (result is Instant) {
                    return DateTimeFormatter.ISO_INSTANT.format(result)
                } else {
                    throw CoercingSerializeException("unsupported value for Instant coersion: $result")
                }
            }

            override fun parseValue(input: Any?): Instant? {
                return if (input == null) null else java.time.Instant.parse(input as String)

            }

            override fun parseLiteral(input: Any?): Instant? {
                return parseValue((input as StringValue).value)
            }
        }).build()
}