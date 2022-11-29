package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent


object EksternVarsling {
    val databaseConfig = Database.config("ekstern_varsling_model")

    private val hendelsestrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "ekstern-varsling-model-builder",
            replayPeriodically = false,
        )
    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val eksternVarslingModelAsync = async {
                EksternVarslingRepository(database.await())
            }

            launch {
                val eksternVarslingModel = eksternVarslingModelAsync.await()
                hendelsestrøm.forEach { event ->
                    eksternVarslingModel.oppdaterModellEtterHendelse(event)
                }
            }

            launch {
                val eksternVarslingRepository = eksternVarslingModelAsync.await()
                val service = EksternVarslingService(
                    eksternVarslingRepository = eksternVarslingRepository,
                    altinnVarselKlient = basedOnEnv(
                        prod = { AltinnVarselKlientImpl() },
                        dev = {
                            AltinnVarselKlientMedFilter(
                                eksternVarslingRepository,
                                AltinnVarselKlientImpl(),
                                AltinnVarselKlientLogging()
                            )
                        },
                        other = { AltinnVarselKlientLogging() },
                    ),
                    hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                )
                service.start(this)
            }

            launchHttpServer(
                httpPort = httpPort,
                customRoute = {
                    val internalTestClient = basedOnEnv(
                        prod = { AltinnVarselKlientImpl() },
                        dev = { AltinnVarselKlientImpl() },
                        other = { AltinnVarselKlientLogging() }
                    )
                    get("/internal/send_sms") {
                        testSms(internalTestClient)
                    }
                    get("/internal/send_epost") {
                        testEpost(internalTestClient)
                    }
                    post("/internal/update_emergency_brake") {
                        updateEmergencyBrake(eksternVarslingModelAsync.await())
                    }
                }
            )
        }
    }
}

data class TestSmsRequestBody(
    val reporteeNumber: String,
    val tlf: String,
    val tekst: String,
)

suspend fun PipelineContext<Unit, ApplicationCall>.testSms(altinnVarselKlient: AltinnVarselKlient) {
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

suspend fun PipelineContext<Unit, ApplicationCall>.testEpost(altinnVarselKlient: AltinnVarselKlient) {
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

private suspend fun PipelineContext<Unit, ApplicationCall>.testSend(
    client: AltinnVarselKlient,
    action: suspend AltinnVarselKlientImpl.() -> Result<AltinnVarselKlient.AltinnResponse>
) {
    if (client is AltinnVarselKlientImpl) {
        client.action()
            .fold(
                onSuccess = {
                    when (it) {
                        is AltinnVarselKlient.AltinnResponse.Ok ->
                            call.respond(HttpStatusCode.OK, laxObjectMapper.writeValueAsString(it.rå))

                        is AltinnVarselKlient.AltinnResponse.Feil ->
                            call.respond(HttpStatusCode.BadRequest, laxObjectMapper.writeValueAsString(it.rå))
                    }
                },
                onFailure = {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        laxObjectMapper.writeValueAsString(
                            mapOf(
                                "type" to it.javaClass.canonicalName,
                                "msg" to it.message,
                            )
                        )
                    )
                }
            )
    }
}

data class UpdateEmergencyBrakeRequestBody(
    val newState: Boolean
)

suspend fun PipelineContext<Unit, ApplicationCall>.updateEmergencyBrake(
    eksternVarslingRepository: EksternVarslingRepository
) {
    val newState = call.receive<UpdateEmergencyBrakeRequestBody>().newState
    eksternVarslingRepository.updateEmergencyBrakeTo(newState)
    call.respond(HttpStatusCode.OK)
}