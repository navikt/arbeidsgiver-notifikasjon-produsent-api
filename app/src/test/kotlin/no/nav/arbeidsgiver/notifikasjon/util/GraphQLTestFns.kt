package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.JsonPath
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ensurePrefix

data class GraphQLError(
    val message: String,
    val locations: Any?,
    val extensions: Map<String, Any>?
)

suspend fun HttpResponse.validateStatusOK() {
    check(status == HttpStatusCode.OK) {
        throw Exception("Expected http status 200. Got $status. Content: '${bodyAsText()}'")
    }
}

suspend inline fun <reified T> HttpResponse.getTypedContent(name: String): T {
    validateStatusOK()
    val errors = getGraphqlErrors()
    if (errors.isEmpty()) {
        val tree = laxObjectMapper.readTree(this.bodyAsText())
        logger().error("content: $tree")
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

suspend fun HttpResponse.getGraphqlErrors(): List<GraphQLError> {
    validateStatusOK()
    val tree = laxObjectMapper.readTree(this.bodyAsText())
    logger().info("content: $tree")
    val errors = tree.get("errors")
    return if (errors == null) emptyList() else laxObjectMapper.convertValue(errors)
}
