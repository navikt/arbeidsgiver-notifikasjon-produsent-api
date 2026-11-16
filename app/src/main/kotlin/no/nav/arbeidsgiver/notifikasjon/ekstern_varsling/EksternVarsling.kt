package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAndSetReady
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent


object EksternVarsling {
    val databaseConfig = Database.config("ekstern_varsling_model")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, configure = {
            connector {
                port = httpPort
            }
            shutdownGracePeriod = 20000
            shutdownTimeout = 30000
        }) {
            val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
            val database = openDatabaseAndSetReady(databaseConfig)
            val eksternVarslingRepository = EksternVarslingRepository(database)

            val hendelsestrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "ekstern-varsling-model-builder",
                replayPeriodically = false,
            )
            val exporterHendelsestrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "ekstern-varsling-status-exporter",
                replayPeriodically = false,
            )

            launch {
                hendelsestrøm.forEach { event ->
                    eksternVarslingRepository.oppdaterModellEtterHendelse(event)
                }
            }

            launch {
                val service = EksternVarslingService(
                    eksternVarslingRepository = eksternVarslingRepository,
                    altinn3VarselKlient = basedOnEnv(
                        prod = { Altinn3VarselKlientImpl() },
                        dev = {
                            Altinn3VarselKlientMedFilter(
                                eksternVarslingRepository,
                                Altinn3VarselKlientLogging()
                            )
                        },
                        other = { Altinn3VarselKlientLogging() },
                    ),
                    hendelseProdusent = hendelseProdusent,
                )
                service.start(this)
            }

            launch {
                val service = EksternVarslingStatusEksportService(
                    eventSource = exporterHendelsestrøm,
                    repo = eksternVarslingRepository,
                )
                service.start(this)
            }

            configureRouting {
                post("/internal/update_emergency_brake") {
                    updateEmergencyBrake(eksternVarslingRepository)
                }
            }
            registerShutdownListener()
        }.start(wait = true)
    }
}


data class UpdateEmergencyBrakeRequestBody(
    val newState: Boolean
)

suspend fun RoutingContext.updateEmergencyBrake(
    eksternVarslingRepository: EksternVarslingRepository
) {
    val newState = call.receive<UpdateEmergencyBrakeRequestBody>().newState
    eksternVarslingRepository.updateEmergencyBrakeTo(newState)
    call.respond(HttpStatusCode.OK)
}