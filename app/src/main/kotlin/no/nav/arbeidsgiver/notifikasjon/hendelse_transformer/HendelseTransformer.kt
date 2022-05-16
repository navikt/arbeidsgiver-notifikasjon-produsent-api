package no.nav.arbeidsgiver.notifikasjon.hendelse_transformer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.convertValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.mapAt
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.JsonNodeKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.kafkaObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent

object HendelseTransformer {
    private val consumer by lazy { JsonNodeKafkaConsumer("hendelse-transformer-1") }
    private val producer by lazy { lagKafkaHendelseProdusent() }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                consumer.forEach { jsonNode ->
                    val hendelse = fiksNumberTilDurationStringISkedulertHardDelete(jsonNode)
                    if (hendelse != null) {
                        producer.send(hendelse)
                    }
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}

fun fiksNumberTilDurationStringISkedulertHardDelete(hendelse: JsonNode): HendelseModel.Hendelse? {
    val nyHendelse = hendelse.mapAt("/hardDelete") { hardDelete ->
        if (hardDelete.get("@type")?.asText() == "Duration") {
           hardDelete.mapAt("/value") { value ->
              if (value.isNumber) {
                  TextNode("PT${value.numberValue().toLong()}S")
              } else {
                  value
              }
           }
        } else {
            hardDelete
        }
    }
    if (nyHendelse == hendelse) {
        return null
    }
    return kafkaObjectMapper.convertValue(nyHendelse)
}