package no.nav.arbeidsgiver.notifikasjon.infrastruktur.json

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

private val objectMapper = jacksonObjectMapper()

fun JsonNode.mapAt(path: JsonPointer, f: (JsonNode) -> JsonNode): JsonNode {
    if (path == JsonPointer.empty())
        return f(this)
    return when (this) {
        is ObjectNode -> {
            fields()
                .asSequence()
                .map {
                    it.key to if (path.matchesProperty(it.key)) {
                        it.value.mapAt(path.tail(), f)
                    } else {
                        it.value
                    }
                }
                .toMap()
                .let(objectMapper::valueToTree)
        }
        is ArrayNode ->
            mapIndexed { index, elem ->
                if (path.matchesElement(index)) {
                    elem.mapAt(path.tail(), f)
                } else {
                    elem
                }
            }
                .let(objectMapper::valueToTree)
        else -> this
    }
}
fun JsonNode.mapAt(path: String, f: (JsonNode) -> JsonNode) =
    mapAt(JsonPointer.compile(path), f)

fun JsonNode.mapText(f: (String) -> String) = TextNode(f(asText()))

fun JsonNode.mapTextAt(path: String, f: (String) -> String): JsonNode =
    mapAt(path) { it.mapText(f) }
