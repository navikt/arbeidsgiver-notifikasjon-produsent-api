package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

class ResumeScheduledJobsService(
    private val eksternVarslingRepository: EksternVarslingRepository,
    private val lokalOsloTid: LokalOsloTid = LokalOsloTidImpl,
) {
    private val log = logger()

    // TODO: launch denne servicen ved oppstart
    suspend fun doWork() {
        val rescheduledCount = eksternVarslingRepository.rescheduleWaitingJobs(lokalOsloTid.nå())
        if (rescheduledCount > 0) {
            log.info("resumed $rescheduledCount jobs")
        }
    }
}
