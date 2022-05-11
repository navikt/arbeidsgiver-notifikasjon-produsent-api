package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import org.apache.kafka.clients.consumer.ConsumerRecord

class BackupRepository(
    private val database: Database,
) {
    suspend fun process(record: ConsumerRecord<ByteArray, ByteArray>) {
        database.transaction {
            if (record.key() != null && record.value() == null) {
                this.delete(record.key())
            } else {
                this.save(record)
            }
        }
    }

    private fun Transaction.delete(key: ByteArray) {
        executeUpdate("""
            update topic_notifikasjon
            set event_value = null
            where event_key = ?
        """) {
            bytea(key)
        }
    }

    private fun Transaction.save(record: ConsumerRecord<ByteArray, ByteArray>) {
        executeUpdate(
        """
            insert into topic_notifikasjon
            (
                partition, -- int not null,
                "offset", -- bigint not null,
                timestamp, -- bigint not null,
                timestamp_type, -- int not null,
                event_key, -- bytea null,
                event_value -- bytea null
                ) values (?, ?, ?, ?, ?, ?)
        """) {
            integer(record.partition())
            long(record.offset())
            long(record.timestamp())
            integer(record.timestampType().id)
            byteaOrNull(record.key())
            byteaOrNull(record.value())
        }
    }
}

