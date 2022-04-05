package no.nav.arbeidsgiver.notifikasjon

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.AltinnVarselKlient
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.AltinnVarselKlientImpl
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.AltinnVarselKlientLogging
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.AltinnVarselKlientMedFilter
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarslingRepository
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarslingService
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.LokalOsloTidImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.TimedContentConverter
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger


object EksternVarsling {
    val log = logger()
    val databaseConfig = Database.config("ekstern_varsling_model")

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val eksternVarslingModelAsync = async {
                EksternVarslingRepository(database.await())
            }

            launch {
                val eksternVarslingModel = eksternVarslingModelAsync.await()
                forEachHendelse("ekstern-varsling-model-builder") { event ->
                    eksternVarslingModel.oppdaterModellEtterHendelse(event)
                }
            }

            launch {
                val eksternVarslingRepository = eksternVarslingModelAsync.await()
                val service = EksternVarslingService(
                    eksternVarslingRepository = eksternVarslingRepository,
                    altinnVarselKlient = basedOnEnv(
                        prod = { AltinnVarselKlientImpl() },
                        dev = { AltinnVarselKlientMedFilter(eksternVarslingRepository, AltinnVarselKlientImpl(), AltinnVarselKlientLogging()) },
                        other = { AltinnVarselKlientLogging() },
                    ),
                    kafkaProducer = createKafkaProducer(),
                    lokalOsloTid = LokalOsloTidImpl,
                )
                service.start(this)
            }

            launch {
                embeddedServer(Netty, port = httpPort) {
                    installMetrics()
                    install(ContentNegotiation) {
                        register(ContentType.Application.Json, TimedContentConverter(JacksonConverter(laxObjectMapper)))
                    }
                    routing {
                        internalRoutes()

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
                }.start(wait = true)
            }
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
                    call.respond(HttpStatusCode.InternalServerError,
                        laxObjectMapper.writeValueAsString(mapOf(
                            "type" to it.javaClass.canonicalName,
                            "msg" to it.message,
                        ))
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