package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.*
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class ResumeScheduledJobsTests: DescribeSpec({

    fun dateTime(hour: String) =
        LocalDateTime.parse("2020-01-01T$hour:00")


    describe("resuming scheduled tasks") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)
        repository.scheduleJob(uuid("0"), dateTime("02"))
        repository.scheduleJob(uuid("1"), dateTime("03"))
        repository.scheduleJob(uuid("2"), dateTime("03"))
        repository.scheduleJob(uuid("3"), dateTime("04"))
        repository.scheduleJob(uuid("4"), dateTime("04"))

        it("job queue initially empty") {
            database.jobQueue() shouldBe emptyList()
            repository.jobQueueCount() shouldBe 0

            database.waitQueue() shouldContainAll (0..4).map { uuid("$it")  }
            repository.waitQueueCount().first shouldBe 5
        }

        repository.rescheduleWaitingJobs(dateTime("02"))

        it("one job requeued") {
            repository.jobQueueCount() shouldBe 1
            database.jobQueue() shouldContainInOrder listOf(uuid("0"))
            repository.waitQueueCount().first shouldBe 4
            database.waitQueue() shouldContainAll (1..4).map { uuid("$it")  }
        }

        repository.rescheduleWaitingJobs(dateTime("03"))

        it("two jobs requeued") {
            repository.jobQueueCount() shouldBe 3
            database.jobQueue() shouldContainInOrder listOf("0", "1", "2").map(::uuid)
            repository.waitQueueCount().first shouldBe 2
            database.waitQueue() shouldContainAll (3..4).map { uuid("$it")  }
        }

        repository.rescheduleWaitingJobs(dateTime("11"))

        it("two more jobs requeued") {
            repository.jobQueueCount() shouldBe 5
            database.jobQueue() shouldContainInOrder listOf("0", "1", "2", "3", "4").map(::uuid)
            repository.waitQueueCount().first shouldBe 0
            database.waitQueue() shouldContainAll emptyList()
        }
    }

    describe("id eksisterer både i job_queue og wait_queue") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)
        // Jobben er fortsatt i job-queuen. Er ikke viktig om den
        // blir "de-duplisert": kan godt forekomme to ganger, men ikke
        // et krav.

        database.putOnJobQueue(uuid("0"))
        repository.scheduleJob(uuid("0"), dateTime("00"))

        repository.rescheduleWaitingJobs(dateTime("01"))

        it("finnes fortsatt i job_queue") {
            database.jobQueue() shouldContain uuid("0")
        }

        it("er fjernet fra wait_queue") {
            database.waitQueue() shouldNotContain uuid("0")
        }
    }

    describe("putter varsel_id flere ganger inn i vente-kø") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)
        repository.scheduleJob(uuid("0"), dateTime("00"))
        repository.scheduleJob(uuid("0"), dateTime("01"))

        it("begge ligger i køen") {
            database.waitQueue() shouldContainExactly listOf(uuid("0"), uuid("0"))
        }

        repository.rescheduleWaitingJobs(dateTime("00"))

        it("første har blitt flyttet") {
            database.jobQueue() shouldContainExactly listOf(uuid("0"))
            database.waitQueue() shouldContainExactly listOf(uuid("0"))
        }

        val id = repository.findJob(Duration.ofMinutes(1))!!
        repository.deleteFromJobQueue(id)
        it("fant riktig jobb") {
            id shouldBe uuid("0")
        }

        repository.rescheduleWaitingJobs(dateTime("01"))
        it("flyttet nok en gang") {
            database.jobQueue() shouldContainExactly listOf(uuid("0"))
            database.waitQueue() shouldBe emptyList()
        }
    }
})

suspend fun Database.jobQueue(): List<UUID> =
    this.nonTransactionalExecuteQuery("""
        select varsel_id from job_queue
        order by id
    """) {
        getObject("varsel_id", UUID::class.java)
    }

suspend fun Database.waitQueue(): List<UUID> =
    this.nonTransactionalExecuteQuery("""
        select varsel_id from wait_queue
    """) {
        getObject("varsel_id", UUID::class.java)
    }

suspend fun Database.putOnJobQueue(varselId: UUID) {
    this.transaction {
        putOnJobQueue(varselId)
    }
}

