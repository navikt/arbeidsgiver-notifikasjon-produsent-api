package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResumeScheduledJobsTest {

    private fun dateTime(hour: String) =
        LocalDateTime.parse("2020-01-01T$hour:00")


    @Test
    fun `resuming scheduled tasks`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)
        repository.scheduleJob(uuid("0"), dateTime("02"))
        repository.scheduleJob(uuid("1"), dateTime("03"))
        repository.scheduleJob(uuid("2"), dateTime("03"))
        repository.scheduleJob(uuid("3"), dateTime("04"))
        repository.scheduleJob(uuid("4"), dateTime("04"))

        // job queue initially empty
        assertEquals(emptyList(), database.jobQueue())
        assertEquals(0, repository.jobQueueCount())

        assertEquals((0..4).map { uuid("$it") }, database.waitQueue())
        assertEquals(5, repository.waitQueueCount().first)

        repository.rescheduleWaitingJobs(dateTime("02"))

        // one job requeued
        assertEquals(1, repository.jobQueueCount())
        assertEquals(listOf(uuid("0")), database.jobQueue())
        assertEquals(4, repository.waitQueueCount().first)
        assertEquals((1..4).map { uuid("$it") }, database.waitQueue())

        repository.rescheduleWaitingJobs(dateTime("03"))

        // two jobs requeued
        assertEquals(3, repository.jobQueueCount())
        assertEquals(listOf("0", "1", "2").map(::uuid), database.jobQueue())
        assertEquals(2, repository.waitQueueCount().first)
        assertEquals((3..4).map { uuid("$it") }, database.waitQueue())

        repository.rescheduleWaitingJobs(dateTime("11"))

        // two more jobs requeued
        assertEquals(5, repository.jobQueueCount())
        assertEquals(listOf("0", "1", "2", "3", "4").map(::uuid), database.jobQueue())
        assertEquals(0, repository.waitQueueCount().first)
        assertEquals(emptyList(), database.waitQueue())
    }

    @Test
    fun `id eksisterer både i job_queue og wait_queue`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)
        // Jobben er fortsatt i job-queuen. Er ikke viktig om den
        // blir "de-duplisert": kan godt forekomme to ganger, men ikke
        // et krav.

        database.putOnJobQueue(uuid("0"))
        repository.scheduleJob(uuid("0"), dateTime("00"))

        repository.rescheduleWaitingJobs(dateTime("01"))

        // finnes fortsatt i job_queue
        assertTrue(
            database.jobQueue().contains(uuid("0"))
        )

        // er fjernet fra wait_queue
        assertFalse(
            database.waitQueue().contains(uuid("0"))
        )
    }

    @Test
    fun `putter varsel_id flere ganger inn i vente-kø`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)
        repository.scheduleJob(uuid("0"), dateTime("00"))
        repository.scheduleJob(uuid("0"), dateTime("01"))

        // begge ligger i køen
        assertEquals(listOf(uuid("0"), uuid("0")), database.waitQueue())

        repository.rescheduleWaitingJobs(dateTime("00"))

        // første har blitt flyttet
        assertEquals(listOf(uuid("0")), database.jobQueue())
        assertEquals(listOf(uuid("0")), database.waitQueue())

        val id = repository.findJob(Duration.ofMinutes(1))!!
        repository.deleteFromJobQueue(id)
        // fant riktig jobb
        assertEquals(uuid("0"), id)

        repository.rescheduleWaitingJobs(dateTime("01"))
        // flyttet nok en gang
        assertEquals(listOf(uuid("0")), database.jobQueue())
        assertEquals(emptyList(), database.waitQueue())
    }
}

suspend fun Database.jobQueue(): List<UUID> =
    this.nonTransactionalExecuteQuery(
        """
        select varsel_id from job_queue
        order by id
    """
    ) {
        getObject("varsel_id", UUID::class.java)
    }

suspend fun Database.waitQueue(): List<UUID> =
    this.nonTransactionalExecuteQuery(
        """
        select varsel_id from wait_queue
    """
    ) {
        getObject("varsel_id", UUID::class.java)
    }

suspend fun Database.putOnJobQueue(varselId: UUID) {
    this.transaction {
        putOnJobQueue(varselId)
    }
}

