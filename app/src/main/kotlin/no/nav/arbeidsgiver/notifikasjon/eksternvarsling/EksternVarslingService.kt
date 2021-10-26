package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Duration

class EksternVarslingService(
    private val eksternVarslingRepository: EksternVarslingRepository,
    private val altinnVarselKlient: AltinnVarselKlient,
) {
    private val log = logger()

    suspend fun automaticUnlockingLoop() {
        while (true) {
            try {
                val released = eksternVarslingRepository.releaseTimedoutLocks()
                if (released.isNotEmpty()) {
                    log.error("released ${released.size} rows. varselId=${released.joinToString(",")}")
                }
            } catch (e: Exception) {
                log.error("error", e)
            }
            delay(Duration.ofMinutes(10).toMillis())
        }
    }

    fun sendVarsel()  {
        val varsel = eksternVarslingRepository.finnOgLåsVarsel()
        /* get next varsel, and "lock" row */
        /* call altinn */
        /* publish result, update db? */
        /* update row: next state */
    }

    fun sendKvittering() {
        /* get next result from db */
    }
}