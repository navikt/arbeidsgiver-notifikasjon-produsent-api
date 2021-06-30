package no.nav.arbeidsgiver.notifikasjon

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractProdusentContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.httpServerSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModelImpl
import org.apache.kafka.clients.consumer.ConsumerConfig

object ProdusentMain {
    val log = logger()

    val databaseConfig = Database.Config(
        host = System.getenv("DB_HOST") ?: "localhost",
        port = System.getenv("DB_PORT") ?: "5432",
        username = System.getenv("DB_USERNAME") ?: "postgres",
        password = System.getenv("DB_PASSWORD") ?: "postgres",
        database = System.getenv("DB_DATABASE") ?: "produsent-model",
        migrationLocations = "db/migration/produsent_model",
    )

    private val defaultAuthProviders = if (System.getenv("NAIS_CLUSTER_NAME") == "prod-gcp")
        listOf(
            HttpAuthProviders.AZURE_AD,
        )
    else
        listOf(
            HttpAuthProviders.AZURE_AD,
            HttpAuthProviders.FAKEDINGS_PRODUSENT
        )

    fun main(
        authProviders: List<JWTAuthentication> = defaultAuthProviders,
        httpPort: Int = 8080,
    ) {
        runBlocking(Dispatchers.Default) {
            val produsentModelAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    ProdusentModelImpl(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            launch {
                ProdusentRegisterImpl.validateAll()
            }

            launch {
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer {
                        put(ConsumerConfig.GROUP_ID_CONFIG, "produsent-model-builder")
                    }
                    val produsentModel = produsentModelAsync.await()

                    kafkaConsumer.forEachEvent { event ->
                        produsentModel.oppdaterModellEtterHendelse(event)
                    }
                }
            }

            val graphql = async {
                ProdusentAPI.newGraphQL(
                    produsentModel = produsentModelAsync.await(),
                    kafkaProducer = createKafkaProducer()
                )
            }

            launch {
                val httpServer = embeddedServer(Netty, port = httpPort, configure = {
                    connectionGroupSize = 16
                    callGroupSize = 16
                    workerGroupSize = 16
                }) {
                    httpServerSetup(
                        authProviders = authProviders,
                        extractContext = extractProdusentContext,
                        graphql = graphql
                    )
                }
                httpServer.start(wait = true)
            }
        }
    }
}