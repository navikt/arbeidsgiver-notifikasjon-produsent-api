package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import kotlin.test.Test
import kotlin.test.assertEquals


class BackupRepositoryTest {

    private val record1 = record(
        partition = 0,
        offset = 0,
        key = "key 1",
        value = "some value 1 ",
    )

    private val record2 = record(
        partition = 0,
        offset = 1,
        key = "key 2",
        value = "some value 2 ",
        headers = RecordHeaders(
            listOf(
                RecordHeader("h1", byteArrayOf(0, 1)),
                RecordHeader("h1", byteArrayOf(0, 1)),
                RecordHeader("h2", byteArrayOf(3, 4)),
                RecordHeader("h1", byteArrayOf()),
                RecordHeader("h1", null)
            )
        )
    )

    private val record3 = record(
        partition = 1,
        offset = 0,
        key = "key 3",
        value = "some value 3 ",
    )

    private val record4 = record(
        partition = 0,
        offset = 2,
        key = "key 1",
        value = null
    )

    private val record5 = record(
        partition = 0,
        offset = 3,
        key = "key 3",
        value = "hey"
    )

    @Test
    fun `Reading from database`() = withTestDatabase(KafkaBackup.databaseConfig) { database ->
        val backupRepository = BackupRepository(database)
        listOf(record1, record2, record3, record4, record5).forEach {
            backupRepository.process(it)
        }

        val `records 1 to 3` = backupRepository.readRecords(limit = 3, offset = 0)
        val `records 4 to 5` = backupRepository.readRecords(limit = 3, offset = 3)

        assertEquals(3, `records 1 to 3`.size)
        assertEquals(2, `records 4 to 5`.size)

        val r1 = `records 1 to 3`[0]
        assertEquals(record1.offset(), r1.offset)
        assertEquals(record1.partition(), r1.partition)
        assertEqualsAsString(record1.key(), r1.key)
        assertEqualsAsString(null /* tombstone! */, r1.value)
        assertEquals(RecordHeaders(listOf()), r1.headers)

        val r2 = `records 1 to 3`[1]
        assertEquals(record2.offset(), r2.offset)
        assertEquals(record2.partition(), r2.partition)
        assertEqualsAsString(record2.key(), r2.key)
        assertEqualsAsString(record2.value(), r2.value)
        assertEquals(
            RecordHeaders(
                listOf(
                    RecordHeader("h1", byteArrayOf(0, 1)),
                    RecordHeader("h1", byteArrayOf(0, 1)),
                    RecordHeader("h2", byteArrayOf(3, 4)),
                    RecordHeader("h1", byteArrayOf()),
                    RecordHeader("h1", null),
                )
            ), r2.headers
        )

        val r3 = `records 1 to 3`[2]
        assertEquals(record3.offset(), r3.offset)
        assertEquals(record3.partition(), r3.partition)
        assertEqualsAsString(record3.key(), r3.key)
        assertEqualsAsString(record3.value(), r3.value)

        val r4 = `records 4 to 5`[0]
        assertEquals(record4.offset(), r4.offset)
        assertEquals(record4.partition(), r4.partition)
        assertEqualsAsString(record4.key(), r4.key)
        assertEqualsAsString(record4.value(), r4.value)

        val r5 = `records 4 to 5`[1]
        assertEquals(record5.offset(), r5.offset)
        assertEquals(record5.partition(), r5.partition)
        assertEqualsAsString(record5.key(), r5.key)
        assertEqualsAsString(record5.value(), r5.value)
    }
}

fun assertEqualsAsString(
    expected: ByteArray?,
    actual: ByteArray?,
) = assertEquals(
    expected = expected?.decodeToString(),
    actual = actual?.decodeToString()
)

fun record(
    partition: Int,
    offset: Long,
    key: String,
    value: String?,
    headers: RecordHeaders = RecordHeaders(),
): ConsumerRecord<ByteArray, ByteArray> {
    val keyBytes = key.toByteArray()
    val valueBytes = value?.toByteArray()
    return ConsumerRecord<ByteArray, ByteArray>(
        "topic",
        partition,
        offset,
        0L,
        TimestampType.NO_TIMESTAMP_TYPE,
        0L,
        keyBytes.size,
        valueBytes?.size ?: 0,
        keyBytes,
        valueBytes,
        headers,
    )
}
