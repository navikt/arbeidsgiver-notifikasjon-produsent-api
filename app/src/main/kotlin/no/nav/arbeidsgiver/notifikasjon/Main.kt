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
                val kafkaConsumer = createKafkaConsumer()
                val queryModel = queryModelAsync.await()

                kafkaConsumer.forEachEvent { event ->
                    queryModel.oppdaterModellEtterHendelse(event)
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
        AuthConfigs.LOGIN_SERVICE,
        AuthConfigs.TOKEN_X,
    )

    val produsentAutentisering = mutableListOf(
        AuthConfigs.AZURE_AD,
    )

    if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp") {
        brukerAutentisering.add(AuthConfigs.FAKEDINGS_BRUKER)
        produsentAutentisering.add(AuthConfigs.FAKEDINGS_PRODUSENT)
    }

    Main.main(
        brukerAutentisering = brukerAutentisering,
        produsentAutentisering = produsentAutentisering,
    )
}

