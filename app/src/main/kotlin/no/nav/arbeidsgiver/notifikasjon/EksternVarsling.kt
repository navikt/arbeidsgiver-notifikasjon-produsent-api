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
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.TimedContentConverter
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import org.apache.kafka.clients.consumer.ConsumerConfig


object EksternVarsling {
    val log = logger()

    val databaseConfig = Database.Config(
        host = System.getenv("DB_HOST") ?: "localhost",
        port = System.getenv("DB_PORT") ?: "5432",
        username = System.getenv("DB_USERNAME") ?: "postgres",
        password = System.getenv("DB_PASSWORD") ?: "postgres",
        database = System.getenv("DB_DATABASE") ?: "ekstern-varsling-model",
        migrationLocations = "db/migration/ekstern_varsling_model",
    )

    fun main(
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val eksternVarslingModelAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    EksternVarslingRepository(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            launch {
                // Hvorfor er det nyttig å kunne skru av? Brukes det?
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer {
                        put(ConsumerConfig.GROUP_ID_CONFIG, "ekstern-varsling-model-builder")
                    }
                    val eksternVarslingModel = eksternVarslingModelAsync.await()

                    kafkaConsumer.forEachEvent { event ->
                        eksternVarslingModel.oppdaterModellEtterHendelse(event)
                    }
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
                        register(ContentType.Application.Json, TimedContentConverter(JacksonConverter(objectMapper)))
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
                            call.respond(HttpStatusCode.OK, objectMapper.writeValueAsString(it.rå))
                        is AltinnVarselKlient.AltinnResponse.Feil ->
                            call.respond(HttpStatusCode.BadRequest, objectMapper.writeValueAsString(it.rå))
                    }
                },
                onFailure = {
                    call.respond(HttpStatusCode.InternalServerError,
                        objectMapper.writeValueAsString(mapOf(
                            "type" to it.javaClass.canonicalName,
                            "msg" to it.message,
                        ))
                    )
                }
            )
    }
}