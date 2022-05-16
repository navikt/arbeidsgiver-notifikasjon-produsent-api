package no.nav.arbeidsgiver.notifikasjon.hendelse_transform

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.mapAt
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.kafkaObjectMapper

fun transform(hendelse: JsonNode): HendelseModel.Hendelse? {
    val nyHendelse = hendelse.mapAt("/hardDelete") { hardDelete ->
        if (hardDelete.get("@type").asText() == "Duration") {
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