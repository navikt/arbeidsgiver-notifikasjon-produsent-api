package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import com.fasterxml.jackson.databind.JsonNode
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.common.serialization.StringDeserializer

class JsonNodeValueDeserializer : JsonDeserializer<JsonNode>(JsonNode::class.java)

class JsonNodeKafkaConsumer(
    groupId: String,
    seekToBeginning: Boolean = false,
    configOverrides: Map<String, Any> = emptyMap(),
) {
    private val log = logger()

    private val consumer = CoroutineKafkaConsumer.new(
        topic = NOTIFIKASJON_TOPIC,
        groupId = groupId,
        keyDeserializer = StringDeserializer::class.java,
        valueDeserializer = JsonNodeValueDeserializer::class.java,
        seekToBeginning = seekToBeginning,
        configOverrides = configOverrides,
    )

    suspend fun forEach(
        body: suspend (JsonNode) -> Unit
    ) {
        consumer.forEach { record ->
            val recordValue = record.value()
            if (recordValue == null) {
                log.info("skipping tombstoned event key=${record.key()}")
            } else {
                body(recordValue)
            }
        }
    }
}



