package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.base64Decoded
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.base64Encoded
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders

/**
 * The code is written to only support one topic. Run multiple copies for the
 * application if you need to have backup of multiple topics.
 */
class BackupRepository(
    private val database: Database,
) {
    private val savedCounter = Metrics.meterRegistry.counter("kafka_backup_saved")
    private val tombstonedCounter = Metrics.meterRegistry.counter("kafka_backup_tombstoned")

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
        tombstonedCounter.increment()
    }

    private fun Transaction.save(record: ConsumerRecord<ByteArray, ByteArray>) {
        val headers = record.headers().map { it.key() to it.value()?.base64Encoded }
        executeUpdate(
        """
            insert into topic_notifikasjon
            (
                partition,
                "offset",
                timestamp,
                timestamp_type,
                headers,
                event_key,
                event_value
                ) values (?, ?, ?, ?, ?::jsonb, ?, ?)
        """) {
            integer(record.partition())
            long(record.offset())
            long(record.timestamp())
            integer(record.timestampType().id)
            jsonb(headers)
            byteaOrNull(record.key())
            byteaOrNull(record.value())
        }
        savedCounter.increment()
    }

    class Record(
        val partition: Int,
        val offset: Long,
        val timestamp: Long,
        val timestampType: Int,
        val key: ByteArray?,
        val value: ByteArray?,
        val headers: RecordHeaders,
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
                    headers = getString("headers")
                        .let { laxObjectMapper.readValue<List<Pair<String, String?>>>(it) }
                        .map { (key, value) -> RecordHeader(key, value?.base64Decoded) }
                        .let { RecordHeaders(it) }
                )
            })
    }
}

