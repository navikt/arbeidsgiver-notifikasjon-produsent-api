package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Duration

// TODO: huske å potensielt implementere cancel-event. jira?
// TODO: dokumentasjon for soft/hard delete: Stopper ikke sending av varsler.

class EksternVarslingService(
    private val eksternVarslingRepository: EksternVarslingRepository,
    private val altinnVarselKlient: AltinnVarselKlient,
) {
    private val log = logger()

    /*
        aiven-hendelse: offset mistes/reset til 0, men database ok
            - lett: Finnes rad? noop

        gcp-hendelse: kommer opp med database tom, offset ok
            - er ikke store konsekvensen om vi ikke gjør noe i det hele tatt

            - fører ikke til dobbel-sending
            - fører varsler er potensielt ikke sendt.
            - finnes backup-er av databasen

            - hvor vanskelig er det å replaye i dette scenarioet?
            - vi vet nøyaktig hvilke offsets som er replay, og hvilke som er nye
            - er det vanskelig å detecte?

        begge: komme opp med tom database, offset = 0
            - autodetect: disable processing
     */

    /*
        pod main:
            if (db.rows == 0) {
                db.stop_processing = true
            }

            launch {
                while (true) {
                    msg = kafka.get()
                    log(msg.timestamp)
                    db.put(msg)
                }
            }


            launch {
                while (true) {
                    if (db.stop_processing) {
                        prometheus("processing_stopped", 1)
                    } else {
                        do_db_jobs()
                    }
                }
            }
     */

    /* TODO: skal vi håndtere at man tømmer database og null-stiller kafka consumer group?
     * - forhindre re-sending ved å "detektere" at man ikke er ajour med kafka-strømmen.
     *
     * - basert på kafka-event-metadata, så kan vi se hvor "gammel" en hendelse er.
     *
     * 1. hvis sendingen ble produsert for lenge siden, marker, ikke send, vent på manuel intervention.
     *   + enkelt system
     *   + mulig å være vilkårlig presis
     *   - manuelt, fare for menneskelig feil
     *   - manuelt, kan gå med mye tid
     *   - manuelt, kan være mye delay før avgjørelse blir tatt
     *   - manuelt, kan være så mye jobb at vi ikke får prioritert det
     *
     * 2. hvis sendingen ble produsert for lenge siden, marker, ikke send.
     *    ha en prosess, som automatisk gjør avgjørelsen.
     *
     *   + Vi sender i hvert fall ikke gamle ting.
     *   - Vi får dobbletsending av nye ting.
     *   - Heurestikk for å avgjøre om noe skal gjøres, som blir vanskeligere jo
     *     nærmere i tid hendelsen ble sent. Ingen klar overgang fra "gamelt" til "nytt".
     *
     * 3. detekter rebuild. ikke prossesr jobs.
     *  - (eldste kafka tid) - (sist lest kafka-tid)
     *
     * 4. oppgi snapshot (event id/offset/tidspunkt), og ikke utfør jobs før man er forbi den.
     *   + utvetydig
     *   + stopp-proessesering-logikk er enkel
     *   - huske-hvor-man-stopper er vanskelig
     *   - fungerer dårlig ved ikke-planlagt reset (id-en man venter til er ikke oppdatert)
     *   - hvis man skal håndtere mister databasen, så må backup-offsettet være lagret utenfor db og kafka.
     *   - vi må, på et eller annet vis, huske event-id-er. Per partisjon? Eller offset per partisjon?
     *      sjekke logger
     *      sjekke kafka-offsets fra gammel consumer group hvis man bytter consumer-group
     */

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