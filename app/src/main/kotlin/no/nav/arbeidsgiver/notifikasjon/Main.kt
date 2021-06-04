package no.nav.arbeidsgiver.notifikasjon

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*

object Main {
    val log = logger()

    fun main(
        brukerAutentisering: List<JWTAuthentication>,
        produsentAutentisering: List<JWTAuthentication>,
        altinn: Altinn = AltinnImpl,
        brreg: Brreg = BrregImpl(),
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val queryModelAsync = async {
                try {
                    val database = Database.openDatabase()
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    QueryModel(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }
            launch {
                ProdusentRegister.validateAll()
            }

            launch {
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer()
                    val queryModel = queryModelAsync.await()

                    kafkaConsumer.forEachEvent { event ->
                        queryModel.oppdaterModellEtterHendelse(event)
                    }
                }
            }

            launch {
                val httpServer = embeddedServer(Netty, port = httpPort, configure = {
                    connectionGroupSize = 16
                    callGroupSize = 16
                    workerGroupSize = 16
                }) {
                    httpServerSetup(
                        brukerAutentisering = brukerAutentisering,
                        produsentAutentisering = produsentAutentisering,
                        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
                            altinn = altinn,
                            brreg = brreg,
                            queryModelFuture = queryModelAsync.asCompletableFuture(),
                            kafkaProducer = createKafkaProducer()
                        ),
                        produsentGraphQL = ProdusentAPI.newGraphQL(createKafkaProducer()),
                    )
                }
                httpServer.start(wait = true)
            }
        }
    }
}

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    val brukerAutentisering = mutableListOf(
        HttpAuthProviders.LOGIN_SERVICE,
        HttpAuthProviders.TOKEN_X,
    )

    val produsentAutentisering = mutableListOf(
        HttpAuthProviders.AZURE_AD,
    )

    if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp") {
        brukerAutentisering.add(HttpAuthProviders.FAKEDINGS_BRUKER)
        produsentAutentisering.add(HttpAuthProviders.FAKEDINGS_PRODUSENT)
    }

    Main.main(
        brukerAutentisering = brukerAutentisering,
        produsentAutentisering = produsentAutentisering,
    )
}

