package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

class ResumeScheduledJobsService(
    private val eksternVarslingRepository: EksternVarslingRepository,

) {
    private val log = logger()

    suspend fun doWork() {
        val rescheduledCount = eksternVarslingRepository.rescheduleWaitingJobs(LokalOsloTid.nå())
        if (rescheduledCount > 0) {
            log.info("resumed $rescheduledCount jobs")
        }
    }
}
