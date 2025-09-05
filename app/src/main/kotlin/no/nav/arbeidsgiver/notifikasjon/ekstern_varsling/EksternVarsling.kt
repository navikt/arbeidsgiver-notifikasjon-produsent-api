package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent


object EksternVarsling {
    val databaseConfig = Database.config("ekstern_varsling_model")

    fun main(httpPort: Int = 8080) = runBlocking {
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

        val database = openDatabaseAsync(databaseConfig).await()
        val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)

        embeddedServer(CIO, port = httpPort) {
            val eksternVarslingRepository = EksternVarslingRepository(database)

            launch {
                hendelsestrøm.forEach { event ->
                    eksternVarslingRepository.oppdaterModellEtterHendelse(event)
                }
            }

            launch {
                val service = EksternVarslingService(
                    eksternVarslingRepository = eksternVarslingRepository,
                    altinn2VarselKlient = basedOnEnv(
                        prod = { Altinn2VarselKlientImpl() },
                        dev = {
                            Altinn2VarselKlientMedFilter(
                                eksternVarslingRepository,
                                Altinn2VarselKlientImpl(),
                                Altinn2VarselKlientLogging()
                            )
                        },
                        other = { Altinn2VarselKlientLogging() },
                    ),
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
                val internalTestClient = basedOnEnv(
                    prod = { Altinn2VarselKlientImpl() },
                    dev = { Altinn2VarselKlientImpl() },
                    other = { Altinn2VarselKlientLogging() }
                )
                get("/internal/send_sms") {
                    testSms(internalTestClient)
                }
                get("/internal/send_epost") {
                    testEpost(internalTestClient)
                }
                post("/internal/update_emergency_brake") {
                    updateEmergencyBrake(eksternVarslingRepository)
                }
            }
        }.start(wait = true)
    }
}

data class TestSmsRequestBody(
    val reporteeNumber: String,
    val tlf: String,
    val tekst: String,
)

suspend fun RoutingContext.testSms(altinnVarselKlient: Altinn2VarselKlient) {
    val varselRequest = call.receive<TestSmsRequestBody>()
    testSend(altinnVarselKlient) {
        sendSms(
            mobilnummer = varselRequest.tlf,
            reporteeNumber = varselRequest.reporteeNumber,
            tekst = varselRequest.tekst,
        )
    }
}

data class TestEpostRequestBody(
    val reporteeNumber: String,
    val epost: String,
    val subject: String,
    val body: String,
)

suspend fun RoutingContext.testEpost(altinnVarselKlient: Altinn2VarselKlient) {
    val varselRequest = call.receive<TestEpostRequestBody>()
    this.testSend(altinnVarselKlient) {
        sendEpost(
            reporteeNumber = varselRequest.reporteeNumber,
            epostadresse = varselRequest.epost,
            tittel = varselRequest.subject,
            tekst = varselRequest.body,
        )
    }
}

private suspend fun RoutingContext.testSend(
    client: Altinn2VarselKlient,
    action: suspend Altinn2VarselKlientImpl.() -> AltinnVarselKlientResponseOrException
) {
    if (client is Altinn2VarselKlientImpl) {
        when (val response = client.action()) {
            is AltinnVarselKlientResponse.Ok ->
                call.respond(HttpStatusCode.OK, laxObjectMapper.writeValueAsString(response.rå))

            is AltinnVarselKlientResponse.Feil ->
                call.respond(HttpStatusCode.BadRequest, laxObjectMapper.writeValueAsString(response.rå))

            is UkjentException ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    laxObjectMapper.writeValueAsString(
                        mapOf(
                            "type" to response.exception.javaClass.canonicalName,
                            "msg" to response.exception.message,
                        )
                    )
                )
        }
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