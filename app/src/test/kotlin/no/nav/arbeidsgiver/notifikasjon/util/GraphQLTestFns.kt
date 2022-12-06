package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.JsonPath
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper

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
        val tree = laxObjectMapper.readTree(this.content!!)
        logger().info("content: $tree")
        val dataNode = tree.get("data")

        return if (name.startsWith("$")) {
            val rawJson = laxObjectMapper.writeValueAsString(JsonPath.read(dataNode.toString(), name))
            laxObjectMapper.readValue(rawJson)
        } else {
            val node = dataNode.at(name.ensurePrefix("/"))
            if (node.isNull || node.isMissingNode) {
                throw Exception("content.data does not contain element '$name' content: $tree")
            }
            laxObjectMapper.convertValue(node)
        }
    } else {
        throw Exception("Got errors $errors")
    }
}

fun TestApplicationResponse.getGraphqlErrors(): List<GraphQLError> {
    validateStatusOK()
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = laxObjectMapper.readTree(this.content!!)
    logger().info("content: $tree")
    val errors = tree.get("errors")
    return if (errors == null) emptyList() else laxObjectMapper.convertValue(errors)
}
