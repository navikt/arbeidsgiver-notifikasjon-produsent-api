package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.module.kotlin.convertValue
import io.ktor.http.*
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

fun TestApplicationResponse.validateStatusOK() {
    val status = this.status()
    check(status == HttpStatusCode.OK) {
        throw Exception("Expected http status 200. Got $status. Content: '${this.content}'")
    }
}

inline fun <reified T> TestApplicationResponse.getTypedContent(name: String): T {
    validateStatusOK()
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
    validateStatusOK()
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val errors = tree.get("errors")
    return if (errors == null) emptyList() else objectMapper.convertValue(errors)
}

fun TestApplicationResponse.getFirstGraphqlError(): GraphQLError {
    return getGraphqlErrors().first()
}


