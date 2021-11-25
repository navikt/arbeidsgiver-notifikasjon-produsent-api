package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Duration
import java.time.LocalDateTime
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
    private val eksternVarslingRepository: EksternVarslingRepository,
    private val altinnVarselKlient: AltinnVarselKlient,
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
) {
    private val log = logger()
    private val emergencyBreakGauge = Health.meterRegistry.gauge("processing.emergency.break", AtomicInteger(0))!!

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
            delay(Duration.ofMinutes(1).toMillis())
            return
        }

        val varselId = eksternVarslingRepository.findJob(
            lockTimeout = Duration.ofMinutes(5)
        )

        if (varselId == null) {
            log.info("ingen varsler å prossessere.")
            delay(Duration.ofSeconds(10).toMillis())
            return
        }

        val varsel = eksternVarslingRepository.findVarsel(varselId)
            ?: run {
                log.info("ingen varsel")
                eksternVarslingRepository.deleteFromJobQueue(varselId)
                return
            }

        when (varsel) {
            is EksternVarselTilstand.Ny -> {
                val kalkulertSendeTidspunkt = when (varsel.data.eksternVarsel.sendeVindu) {
                    EksterntVarselSendingsvindu.NKS_ÅPNINGSTID -> LokalOsloTid.nesteNksÅpningstid()
                    EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG -> LokalOsloTid.nesteDagtidIkkeSøndag()
                    EksterntVarselSendingsvindu.LØPENDE -> LokalOsloTid.nå()
                    EksterntVarselSendingsvindu.SPESIFISERT -> varsel.data.eksternVarsel.sendeTidspunkt!!
                }
                /**
                 * TODO: justere grenseverdi?
                 * hvis 1-2 timer eller mer bør tidspunkt kanskje sendes med til altinn / provider ?
                 */
                if (kalkulertSendeTidspunkt.isAfter(LocalDateTime.now().plusHours(2))) {
                    eksternVarslingRepository.scheduleJob(varselId, kalkulertSendeTidspunkt)
                } else {
                    altinnVarselKlient.send(varsel.data.eksternVarsel).fold(
                        onSuccess = { response ->
                            eksternVarslingRepository.markerSomSendtAndReleaseJob(varselId, response)
                        },
                        onFailure = {
                            eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)
                            throw it
                        },
                    )
                }
            }

            is EksternVarselTilstand.Utført -> {
                try {
                    kafkaProducer.sendHendelse(varsel.toHendelse())
                    eksternVarslingRepository.markerSomKvittertAndDeleteJob(varselId)
                } catch (e: Exception) {
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