package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.convertValue
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper

data class GraphQLError(
    val message: String,
    val locations: Any?,
    val extensions: Map<String, Any>?
)

fun String.ensurePrefix(prefix: String) =
    prefix + removePrefix(prefix)

inline fun <reified T> TestApplicationResponse.getTypedContent(name: String): T {
    val errors = getGraphqlErrors()
    if (errors.isEmpty()) {
        val tree = objectMapper.readTree(this.content!!)
        logger().info("content: $tree")
        val node = tree.get("data").at(name.ensurePrefix("/"))
        return objectMapper.convertValue(node)
    } else {
        throw Exception("Got errors $errors")
    }
}

fun TestApplicationResponse.getGraphqlErrors(): List<GraphQLError> {
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val errors = tree.get("errors")
    return if (errors == null) emptyList() else objectMapper.convertValue(errors)
}


