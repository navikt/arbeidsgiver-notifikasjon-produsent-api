package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.rethrowIfCancellation
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
    private val altinn3VarselKlient: Altinn3VarselKlient,
    private val hendelseProdusent: HendelseProdusent,
    private val recheckEmergencyBrakeDelay: Duration = Duration.ofMinutes(1),
    private val idleSleepDelay: Duration = Duration.ofSeconds(10),
) {
    private val log = logger()
    private val emergencyBreakGauge = Metrics.meterRegistry.gauge("processing.emergency.break", AtomicInteger(0))!!
    private val jobQueueSizeGauge = Metrics.meterRegistry.gauge("jobqueue.size", AtomicInteger(0))!!
    private val waitQueuePastSizeGauge =
        Metrics.meterRegistry.gauge("waitqueue.size", Tags.of("resume_job_at", "past"), AtomicInteger(0))!!
    private val waitQueueFutureSizeGauge =
        Metrics.meterRegistry.gauge("waitqueue.size", Tags.of("resume_job_at", "future"), AtomicInteger(0))!!

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
                    log.warn(
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
            when (varsel.data.eksternVarsel) {
                is EksternVarsel.Altinntjeneste -> {
                    with(varsel.data.eksternVarsel as EksternVarsel.Altinntjeneste) {
                        val ressursId = resolveRessursId(
                            serviceCode = serviceCode,
                            serviceEdition = serviceEdition,
                            produsentId = varsel.data.produsentId,
                        )
                        if (ressursId == null) {
                            log.error("Altinn 2 er avviklet. Markerer varsel ${varsel.data.varselId} som feilet.")
                            eksternVarslingRepository.markerSomSendtAndReleaseJob(varsel.data.varselId,
                                Altinn3VarselKlient.ErrorResponse(
                                    rå = TextNode.valueOf("Altinn 2 er avviklet"),
                                    code = "altinn2_avviklet",
                                    message = "Altinn 2 er avviklet. Varselet kan ikke sendes.",
                                )
                            )
                        } else {
                            log.info("Ruter Altinntjeneste-varsel ${varsel.data.varselId} til ressurs $ressursId")
                            altinn3VarselHandler(
                                varsel.withEksternVarsel(
                                    EksternVarsel.Altinnressurs(
                                        fnrEllerOrgnr = fnrEllerOrgnr,
                                        sendeVindu = sendeVindu,
                                        sendeTidspunkt = sendeTidspunkt,
                                        resourceId = ressursId,
                                        epostTittel = tittel,
                                        epostInnhold = innhold,
                                        smsInnhold = tittel,
                                        ordreId = null,
                                    )
                                )
                            )
                        }
                    }
                }
                is EksternVarsel.Sms,
                is EksternVarsel.Epost,
                is EksternVarsel.Altinnressurs -> altinn3VarselHandler(varsel)
            }
        }
    }

    private suspend fun altinn3VarselHandler(varsel: EksternVarselTilstand) {
        val varselId = varsel.data.varselId
        when (varsel) {
            is EksternVarselTilstand.Ny -> {
                val kalkulertSendeTidspunkt =
                    varsel.kalkuertSendetidspunkt(åpningstider, now = osloTid.localDateTimeNow())
                if (kalkulertSendeTidspunkt <= osloTid.localDateTimeNow()) {
                    when (val response = altinn3VarselKlient.order(varsel.data.eksternVarsel, varsel.data.varselId.toString())) {
                        is Altinn3VarselKlient.OrderResponse.Success -> {
                            eksternVarslingRepository.markerSomSendtAndReleaseJob(varselId, response)
                        }

                        is Altinn3VarselKlient.ErrorResponse -> {
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
                    }
                } else {
                    eksternVarslingRepository.scheduleJob(varselId, kalkulertSendeTidspunkt)
                }
            }

            is EksternVarselTilstand.Sendt -> {
                try {
                    when (varsel.response) {
                        is AltinnResponse.Ok -> {
                            val shipmentId = varsel.response.rå["notification"]?.get("shipmentId")?.asText()
                                ?: varsel.response.rå["orderId"]?.asText()
                                ?: ""
                            if (shipmentId.isEmpty())
                                throw RuntimeException("Ordre er markert som sendt, men mangler shipmentId")
                            val (ordreStatus, altinnResponse) = hentShipmentStatus(shipmentId)
                            when (ordreStatus) {
                                Altinn3VarselStatus.Prosessert,
                                Altinn3VarselStatus.Prosesserer -> eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)

                                Altinn3VarselStatus.Kansellert,
                                Altinn3VarselStatus.Kvittert -> {
                                    hendelseProdusent.send(varsel.copy(response = AltinnResponse.Ok(altinnResponse)).toHendelse())
                                    eksternVarslingRepository.markerSomKvittertAndDeleteJob(varselId)
                                }
                                Altinn3VarselStatus.KvittertMedFeil -> {
                                    val (feilkode, feilmelding) = altinnResponse.extractStatusAndDescription()
                                    hendelseProdusent.send(varsel.copy(response = AltinnResponse.Feil(altinnResponse, feilkode, feilmelding)).toHendelse())
                                    eksternVarslingRepository.markerSomKvittertAndDeleteJob(varselId)
                                }
                            }
                        }

                        is AltinnResponse.Feil -> {
                            eksternVarslingRepository.markerSomKvittertAndDeleteJob(varselId)
                            hendelseProdusent.send(varsel.toHendelse())
                        }
                    }
                } catch (e: RuntimeException) {
                    e.rethrowIfCancellation()
                    log.error("Exception producing kafka-kvittering", e)
                    eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)
                }
            }

            is EksternVarselTilstand.Kansellert -> {
                try {
                    hendelseProdusent.send(varsel.toHendelse())
                    eksternVarslingRepository.deleteFromJobQueue(varselId)
                } catch (e: RuntimeException) {
                    e.rethrowIfCancellation()
                    log.error("Exception producing kafka-kvittering", e)
                    eksternVarslingRepository.returnToJobQueue(varsel.data.varselId)
                }
            }

            is EksternVarselTilstand.Kvittert -> {
                eksternVarslingRepository.deleteFromJobQueue(varselId)
            }
        }
    }

    enum class Altinn3VarselStatus {
        Prosesserer,
        Prosessert,
        Kvittert,
        KvittertMedFeil,
        Kansellert
    }


    private suspend fun hentShipmentStatus(shipmentId: String): Pair<Altinn3VarselStatus, JsonNode> {
        val shipment = altinn3VarselKlient.shipment(shipmentId)
        if (shipment !is Altinn3VarselKlient.ShipmentResponse.Success) {
            log.error("Feil ved henting av shipment status ${shipment.rå}")
            return Pair(Altinn3VarselStatus.Prosesserer, shipment.rå)
        }

        return when {
            shipment.isOrderProcessing ->
                Pair(Altinn3VarselStatus.Prosesserer, shipment.rå)

            shipment.isOrderProcessed ->
                Pair(Altinn3VarselStatus.Prosessert, shipment.rå)

            shipment.isOrderCancelled || shipment.isOrderConditionNotMet ->
                Pair(Altinn3VarselStatus.Kansellert, shipment.rå)

            shipment.isOrderCompleted -> {
                // Orderen er ferdig prosessert. Sjekker status på mottakere.
                if (shipment.recipients.any { it.isProcessing }) {
                    return Pair(Altinn3VarselStatus.Prosesserer, shipment.rå)
                }

                if (shipment.allRecipientsDelivered)
                    return Pair(Altinn3VarselStatus.Kvittert, shipment.rå)

                if (shipment.allRecipientsFailed) {
                    return Pair(Altinn3VarselStatus.KvittertMedFeil, shipment.rå)
                }

                if (shipment.hasFailedRecipients) {
                    val failedCount = shipment.recipients.count { it.isFailed }
                    log.warn("Eksternt varsel med shipment id $shipmentId er fullført, men har ikke klart å sende ut alle notifikasjoner. antall feilet: $failedCount, totalt: ${shipment.recipients.size}")
                }

                Pair(Altinn3VarselStatus.Kvittert, shipment.rå)
            }

            else -> {
                throw RuntimeException("Fikk ${shipment.status} fra Altinn, denne blir ikke håndtert")
            }
        }
    }


    companion object {
        private val altinntjenesteToRessursMap: Map<Pair<String, String>, List<String>> = mapOf(
            "nav_permittering-og-nedbemmaning_innsyn-i-alle-innsendte-meldinger" to ("5810" to "1"),
            "nav_sosialtjenester_digisos-avtale" to ("5867" to "1"),
            "nav_forebygge-og-redusere-sykefravar_sykefravarsstatistikk" to ("3403" to basedOnEnv(
                prod = { "2" },
                other = { "1" })),
            "nav_tiltak_arbeidstrening" to ("5332" to basedOnEnv(prod = { "2" }, other = { "1" })),
            "nav_utbetaling_endre-kontonummer-refusjon-arbeidsgiver" to ("2896" to "87"),
            "nav_tiltak_midlertidig-lonnstilskudd" to ("5516" to "1"),
            "nav_tiltak_varig-lonnstilskudd" to ("5516" to "2"),
            "nav_tiltak_sommerjobb" to ("5516" to "3"),
            "nav_tiltak_mentor" to ("5516" to "4"),
            "nav_tiltak_inkluderingstilskudd" to ("5516" to "5"),
            "nav_tiltak_varig-tilrettelagt-arbeid-ordinaer" to ("5516" to "6"),
            "nav_tiltak_adressesperre" to ("5516" to "7"),
            "nav_tiltak_tilskuddsbrev" to ("5278" to "1"),
            "nav_tiltak_ekspertbistand" to ("5384" to "1"),
            "nav_foreldrepenger_inntektsmelding" to ("4936" to "1"),
            "nav_sykepenger_inntektsmelding" to ("4936" to "1"),
            "nav_sykepenger_fritak-arbeidsgiverperiode" to ("4936" to "1"),
            "nav_sykdom-i-familien_inntektsmelding" to ("4936" to "1"),
            "nav_arbeidsforhold_aa-registeret-innsyn-arbeidsgiver" to ("5441" to "1"),
            "nav_arbeidsforhold_aa-registeret-brukerstotte" to ("5441" to "2"),
            "nav_arbeidsforhold_aa-registeret-sok-tilgang" to ("5719" to "1"),
            "nav_arbeidsforhold_aa-registeret-oppslag-samarbeidspartnere" to ("5723" to "1"),
            "nav_rekruttering_kandidater" to ("5078" to "1"),
            "nav_yrkesskade_skademelding" to ("5902" to "1"),
        ).entries.groupBy({ it.value }, { it.key })

        private val produsentIdTilRessursForServiceCode4936 = mapOf(
            "fritakagp" to "nav_sykepenger_fritak-arbeidsgiverperiode",
            "im-notifikasjon" to "nav_sykepenger_inntektsmelding",
            "fp-inntektsmelding-notifikasjon" to "nav_foreldrepenger_inntektsmelding",
            "k9-inntektsmelding-notifikasjon" to "nav_sykdom-i-familien_inntektsmelding",
        )

        private fun resolveRessursId(serviceCode: String, serviceEdition: String, produsentId: String): String? {
            val candidates = altinntjenesteToRessursMap[serviceCode to serviceEdition] ?: return null
            return when {
                candidates.size == 1 -> candidates.single()
                candidates.size > 1 -> produsentIdTilRessursForServiceCode4936[produsentId]?.takeIf { it in candidates }
                else -> null
            }
        }
    }
}

/**
 * Extracts status and description from the given JsonNode.
 * Supports both the new /future/shipment format and legacy formats.
 */
private fun JsonNode.extractStatusAndDescription(): Pair<String, String> {

    // Case 1: New /future/shipment format. Object with status and recipients array.
    val shipmentStatus = at("/status")
    val recipients = at("/recipients")
    if (!shipmentStatus.isMissingNode && recipients.isArray) {
        val failedRecipients = recipients.filter { it.at("/status").asText().contains("Failed") }
            .map { it.at("/status").asText() to (it.at("/destination")?.asText("ukjent") ?: "ukjent") }
        return if (failedRecipients.isNotEmpty()) {
            failedRecipients.map { it.first }.toSortedSet().joinToString(",") to failedRecipients.joinToString(",") { "Destinasjon: ${it.second}, Status: ${it.first}" }
        } else {
            shipmentStatus.asText() to ""
        }
    }

    // Case 2: Legacy ordrestatus. Object with processingStatus
    val statusNode = at("/processingStatus/status")
    val descNode = at("/processingStatus/description")
    if (!statusNode.isMissingNode && !descNode.isMissingNode) {
        return statusNode.asText() to descNode.asText()
    }

    // Case 3: Legacy notifications: Array with notifications
    if (isArray) {
        return flatMap { order ->
            val notifications = order.at("/notifications")
            if (notifications.isArray) {
                notifications.mapNotNull { notification ->
                    val status = notification.at("/sendStatus/status").asText()
                    val desc = notification.at("/sendStatus/description").asText()
                    if (status.isNotEmpty() && desc.isNotEmpty()) status to desc else null
                }
            } else emptyList()
        }.unzip<String, String>().let { (statuses, descriptions) ->
            statuses.joinToString(",") to descriptions.joinToString(",")
        }
    }

    logger().error("Could not extract status and description from JsonNode. This should not happen.")
    return "ukjent" to ""
}
