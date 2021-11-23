package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.LocalDateTime
import java.time.ZoneId

class ResumeScheduledJobsService(
    private val eksternVarslingRepository: EksternVarslingRepository,

) {
    private val norwayZoneId = ZoneId.of("Europe/Oslo")
    private val log = logger()

    suspend fun doWork() {
        val currentLocalTime = LocalDateTime.now(norwayZoneId)
        val rescheduledCount = eksternVarslingRepository.rescheduleWaitingJobs(currentLocalTime)
        if (rescheduledCount > 0) {
            log.info("resumed $rescheduledCount jobs")
        }
    }
}
