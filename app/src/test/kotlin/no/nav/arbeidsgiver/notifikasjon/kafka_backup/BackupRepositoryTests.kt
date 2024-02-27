package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType


class BackupRepositoryTests : DescribeSpec({

    val record1 = record(
        partition = 0,
        offset = 0,
        key = "key 1",
        value ="some value 1 ",
    )

    val record2 = record(
        partition = 0,
        offset = 1,
        key = "key 2",
        value ="some value 2 ",
        headers = RecordHeaders(listOf(
            RecordHeader("h1", byteArrayOf(0, 1)),
            RecordHeader("h1", byteArrayOf(0, 1)),
            RecordHeader("h2", byteArrayOf(3, 4)),
            RecordHeader("h1", byteArrayOf()),
            RecordHeader("h1", null)
        ))
    )

    val record3 = record(
        partition = 1,
        offset = 0,
        key = "key 3",
        value ="some value 3 ",
    )

    val record4 = record(
        partition = 0,
        offset = 2,
        key = "key 1",
        value = null
    )

    val record5 = record(
        partition = 0,
        offset = 3,
        key = "key 3",
        value = "hey"
    )

    describe("Reading from database") {
        val database = testDatabase(KafkaBackup.databaseConfig)
        val backupRepository = BackupRepository(database)
        listOf(record1, record2, record3, record4, record5).forEach {
            backupRepository.process(it)
        }

        val `records 1 to 3` = backupRepository.readRecords(limit = 3, offset = 0)
        val `records 4 to 5` = backupRepository.readRecords(limit = 3, offset = 3)

        it("should read correct values") {
            `records 1 to 3` shouldHaveSize 3
            `records 4 to 5` shouldHaveSize 2

            val r1 = `records 1 to 3`[0]
            r1.offset shouldBe record1.offset()
            r1.partition shouldBe record1.partition()
            r1.key shouldBe record1.key()
            r1.value shouldBe null /* tombstone! */
            r1.headers shouldBe RecordHeaders(listOf())

            val r2 = `records 1 to 3`[1]
            r2.offset shouldBe record2.offset()
            r2.partition shouldBe record2.partition()
            r2.key shouldBe record2.key()
            r2.value shouldBe record2.value()
            r2.headers shouldBe RecordHeaders(listOf(
                RecordHeader("h1", byteArrayOf(0, 1)),
                RecordHeader("h1", byteArrayOf(0, 1)),
                RecordHeader("h2", byteArrayOf(3, 4)),
                RecordHeader("h1", byteArrayOf()),
                RecordHeader("h1", null),
            ))

            val r3 = `records 1 to 3`[2]
            r3.offset shouldBe record3.offset()
            r3.partition shouldBe record3.partition()
            r3.key shouldBe record3.key()
            r3.value shouldBe record3.value()

            val r4 = `records 4 to 5`[0]
            r4.offset shouldBe record4.offset()
            r4.partition shouldBe record4.partition()
            r4.key shouldBe record4.key()
            r4.value shouldBe record4.value()

            val r5 = `records 4 to 5`[1]
            r5.offset shouldBe record5.offset()
            r5.partition shouldBe record5.partition()
            r5.key shouldBe record5.key()
            r5.value shouldBe record5.value()
        }
    }

})

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
