package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
 * The code is written to only support one topic. Run multiple copies for the
 * application if you need to have backup of multiple topics.
 */
class BackupRepository(
    private val database: Database,
) {
    suspend fun process(record: ConsumerRecord<ByteArray, ByteArray>) {
        database.transaction {
            this.save(record)

            if (record.key() != null && record.value() == null) {
                this.delete(record.key())
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

    class Record(
        val partition: Int,
        val offset: Long,
        val timestamp: Long,
        val timestampType: Int,
        val key: ByteArray?,
        val value: ByteArray?,
    )

    suspend fun readRecords(offset: Long, limit: Long): List<Record> {
        return database.nonTransactionalExecuteQuery(
            """
                select * from topic_notifikasjon
                order by id
                offset ?
                limit ?
            """,
            setup = {
                long(offset)
                long(limit)
            },
            transform = {
                Record(
                    partition = getInt("partition"),
                    offset = getLong("offset"),
                    timestamp = getLong("timestamp"),
                    timestampType = getInt("timestamp_type"),
                    key = getBytes("event_key"),
                    value = getBytes("event_value"),
                )
            })
    }
}

