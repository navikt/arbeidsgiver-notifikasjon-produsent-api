package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTidImpl
import org.slf4j.event.Level.ERROR
import org.slf4j.event.Level.WARN
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/*
 *
 *  En naiv implementasjon vil kunne sende gamle sms/epost på nytt hvis det er
 *  no feil med infrastrukturen. Det er spesielt tre tilfeller vi har studert:
 *
 *  aiven-hendelse: offset mistes/reset til 0, men database ok
 *      - lett: Finnes rad? noop
 *
 *  gcp-hendelse: kommer opp med database tom, offset ok
 *      - er ikke store konsekvensen om vi ikke gjør noe i det hele tatt
 *
 *      - fører ikke til dobbel-sending
 *      - fører varsler er potensielt ikke sendt.
 *      - finnes backup-er av databasen
 *
 *      - hvor vanskelig er det å replaye i dette scenarioet?
 *      - vi vet nøyaktig hvilke offsets som er replay, og hvilke som er nye
 *      - er det vanskelig å detecte?
 *
 *  begge: komme opp med tom database, offset = 0
 *      - autodetect: disable processing
 *
 *
 *  Vi har kommet fram til følgende pseudo-kode.
 *
 *  pod main:
 *      if (db.rows == 0) {
 *          db.stop_processing = true
 *      }
 *
 *      launch {
 *          while (true) {
 *              msg = kafka.get()
 *              log(msg.timestamp)
 *              db.put(msg)
 *          }
 *      }
 *
 *
 *      launch {
 *          while (true) {
 *              if (db.stop_processing) {
 *                  prometheus("processing_stopped", 1)
 *              } else {
 *                  do_db_jobs()
 *              }
 *          }
 *      }
 *
 * Andre notater:
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


class EksternVarslingService(
    private val åpningstider: Åpningstider = ÅpningstiderImpl,
    private val osloTid: OsloTid = OsloTidImpl,
    private val eksternVarslingRepository: EksternVarslingRepository,
    private val altinn2VarselKlient: Altinn2VarselKlient,
    private val altinn3VarselKlient: Altinn3VarselKlient,
    private val hendelseProdusent: HendelseProdusent,
    private val recheckEmergencyBrakeDelay: Duration = Duration.ofMinutes(1),
    private val idleSleepDelay: Duration = Duration.ofSeconds(10),
) {
    private val log = logger()
    private val emergencyBreakGauge = Metrics.meterRegistry.gauge("processing.emergency.break", AtomicInteger(0))!!
    private val jobQueueSizeGauge = Metrics.meterRegistry.gauge("jobqueue.size", AtomicInteger(0))!!
    private val waitQueuePastSizeGauge = Metrics.meterRegistry.gauge("waitqueue.size", Tags.of("resume_job_at", "past"), AtomicInteger(0))!!
    private val waitQueueFutureSizeGauge = Metrics.meterRegistry.gauge("waitqueue.size", Tags.of("resume_job_at", "future"), AtomicInteger(0))!!

    fun start(coroutineScope: CoroutineScope): Job {
        return coroutineScope.launch {
            launchProcessingLoop(
                "hard-delete",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                eksternVarslingRepository.deleteScheduledHardDeletes()
            }

            launchProcessingLoop(
                "release-locks",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                val released = eksternVarslingRepository.releaseTimedOutJobLocks()
                if (released.isNotEmpty()) {
                    log.error(
                        """
                    Found ${released.size} abandoned jobs.
                    Returning to job queue ${released.joinToString(", ")}.
                    """.trimMargin()
                    )
                }
            }

            launchProcessingLoop(
                "requeue-lost-work",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                eksternVarslingRepository.createJobsForAbandonedVarsler()
            }

            launchProcessingLoop(
                "gauge-oppdatering",
                pauseAfterEach = Duration.ofMinutes(1)
            ) {
                jobQueueSizeGauge.set(eksternVarslingRepository.jobQueueCount())
                eksternVarslingRepository.waitQueueCount().let { (past, future) ->
                    waitQueuePastSizeGauge.set(past)
                    waitQueueFutureSizeGauge.set(future)
                }
                log.info("gauge-oppdatering vellykket")
            }

            launchProcessingLoop(
                "resume-scheduled-work",
                pauseAfterEach = Duration.ofMinutes(5)
            ) {
                val rescheduledCount = eksternVarslingRepository.rescheduleWaitingJobs(osloTid.localDateTimeNow())
                if (rescheduledCount > 0) {
                    log.info("resumed $rescheduledCount jobs from wait queue")
                }
            }

            launchProcessingLoop(
                "ekstern-varsel",
                init = { eksternVarslingRepository.detectEmptyDatabase() }
            ) {
                workOnEksternVarsel()
            }
        }
    }

    private suspend fun workOnEksternVarsel() {
        val emergencyBreakOn = eksternVarslingRepository.emergencyBreakOn()
            .also {
                emergencyBreakGauge.set(if (it) 1 else 0)
            }
        if (emergencyBreakOn) {
            log.info("processing is disabled. will check again later.")
            delay(recheckEmergencyBrakeDelay.toMillis())
            return
        }

        val varselId = eksternVarslingRepository.findJob(
            lockTimeout = Duration.ofMinutes(5)
        )

        if (varselId == null) {
            log.info("ingen varsler å prossessere.")
            delay(idleSleepDelay.toMillis())
            return
        }

        val varsel = eksternVarslingRepository.findVarsel(varselId)
            ?: run {
                log.info("ingen varsel $varselId knyttet til jobb")
                eksternVarslingRepository.deleteFromJobQueue(varselId)
                return
            }

        withContext(varsel.asMDCContext()) {
            //
            when (varsel) {
                is EksternVarselTilstand.Ny -> {
                    val kalkulertSendeTidspunkt = varsel.kalkuertSendetidspunkt(åpningstider, now = osloTid.localDateTimeNow())
                    if (kalkulertSendeTidspunkt <= osloTid.localDateTimeNow()) {
                        if (varsel.data.eksternVarsel is EksternVarsel.Altinntjeneste) {
                            /**
                             * Bruker kun altinn 2 klient for varsel på serviceCode:serviceEdition
                             * Alle andre varsler sendes med altinn 3 klient.
                             * Når Altinn 2 fases ut kan denne if-sjekken fjernes, og altinn 2 klient kan fjernes.
                             */
                            when (val response = altinn2VarselKlient.send(varsel.data.eksternVarsel)) {
                                is AltinnVarselKlientResponse.Ok -> {
                                    eksternVarslingRepository.markerSomSendtAndReleaseJob(varselId, response)
                                }
                                is AltinnVarselKlientResponse.Feil -> {
                                    if (response.isRetryable()) {
                                        log.error("Retryable feil fra altinn ved sending av notifikasjon: {}", response)
                                        eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)
                                    } else {
                                        log.atLevel(
                                            if (response.isSupressable()) WARN
                                            else ERROR
                                        ).log("Ikke-retryable feil fra altinn ved sending av notifikasjon: {}:", response)
                                        eksternVarslingRepository.markerSomSendtAndReleaseJob(varselId, response)
                                    }
                                }
                                is UkjentException -> {
                                    eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)
                                    throw response.exception
                                }
                            }
                        } else {
                            when (val response = altinn3VarselKlient.order(varsel.data.eksternVarsel)) {
                                is Altinn3VarselKlient.OrderResponse.Success ->
                                    eksternVarslingRepository.markerSomSendtAndReleaseJob(varselId, response)

                                is Altinn3VarselKlient.ErrorResponse -> {
                                    // fra api dokumentasjonen ser det ikke ut som vi kan få noen retryable feil
                                    log.error("Ikke-retryable feil fra altinn ved sending av notifikasjon: {}:", response)
                                    eksternVarslingRepository.markerSomSendtAndReleaseJob(varselId, response)
                                }
                            }

                        }
                    } else {
                        eksternVarslingRepository.scheduleJob(varselId, kalkulertSendeTidspunkt)
                    }
                }

                is EksternVarselTilstand.Sendt -> {
                    try {
                        hendelseProdusent.send(varsel.toHendelse())
                        eksternVarslingRepository.markerSomKvittertAndDeleteJob(varselId)
                    } catch (e: RuntimeException) {
                        log.error("Exception producing kafka-kvittering", e)
                        eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)
                    }
                }

                is EksternVarselTilstand.Kansellert -> {
                    try {
                        hendelseProdusent.send(varsel.toHendelse())
                        eksternVarslingRepository.deleteFromJobQueue(varselId)
                    } catch (e: RuntimeException) {
                        log.error("Exception producing kafka-kvittering", e)
                        eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)
                    }
                }

                is EksternVarselTilstand.Kvittert -> {
                    eksternVarslingRepository.deleteFromJobQueue(varselId)
                }
            }
        }
    }
}
