package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Duration

class EksternVarslingService(
    private val eksternVarslingRepository: EksternVarslingRepository,
    private val altinnVarselKlient: AltinnVarselKlient,
) {
    private val log = logger()

    suspend fun releaseAbandonedLocksLoop() {
        while (true) {
            try {
                val released = eksternVarslingRepository.releaseAbandonedLocks()
                if (released.isNotEmpty()) {
                    log.error(
                        """
                        Found ${released.size} abandoned jobs.
                        Returning to job queue ${released.joinToString(", ")}.
                        """.trimMargin()
                    )
                }
            } catch (e: Exception) {
                log.error("Releasing abandoned jobs failed with exception.", e)
            }
            delay(Duration.ofMinutes(10).toMillis())
        }
    }

    suspend fun sendVarsel()  {
        val varsel = eksternVarslingRepository.findWork()
        /* get next varsel, and "lock" row */
        /* call altinn */
        /* publish result, update db? */
        /* update row: next state */
    }

    fun sendKvittering() {
        /* get next result from db */
    }
}