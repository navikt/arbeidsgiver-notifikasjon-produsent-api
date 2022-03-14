package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.utils.Utils

class OrgnrPartitioner : Partitioner {
    companion object {
        fun partitionOfOrgnr(orgnr: String, numPartitions: Int): Int =
            Utils.toPositive(Utils.murmur2(orgnr.toByteArray())) % numPartitions
    }

    override fun partition(
        topic: String,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster
    ): Int =
        when (value) {
            is Hendelse -> partitionOfOrgnr(value.virksomhetsnummer, cluster.partitionsForTopic(topic).size)
            null -> throw IllegalArgumentException("OrgnrPartition skal ikke motta tombstone-records")
            else -> throw IllegalArgumentException("Ukjent event-type ${value::class.qualifiedName}")
        }

    override fun configure(configs: MutableMap<String, *>?) {
    }

    override fun close() {
    }
}

