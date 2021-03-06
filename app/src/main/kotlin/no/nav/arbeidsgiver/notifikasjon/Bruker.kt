package no.nav.arbeidsgiver.notifikasjon

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.httpServerSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import org.apache.kafka.clients.consumer.ConsumerConfig


object Bruker {
    val log = logger()

    val databaseConfig = Database.Config(
        host = System.getenv("DB_HOST") ?: "localhost",
        port = System.getenv("DB_PORT") ?: "5432",
        username = System.getenv("DB_USERNAME") ?: "postgres",
        password = System.getenv("DB_PASSWORD") ?: "postgres",
        database = System.getenv("DB_DATABASE") ?: "bruker-model",
        migrationLocations = "db/migration/bruker_model",
    )

    private val defaultAuthProviders = if (System.getenv("NAIS_CLUSTER_NAME") == "prod-gcp")
        listOf(
            HttpAuthProviders.LOGIN_SERVICE,
            HttpAuthProviders.TOKEN_X,
        )
    else
        listOf(
            HttpAuthProviders.LOGIN_SERVICE,
            HttpAuthProviders.TOKEN_X,
            HttpAuthProviders.FAKEDINGS_BRUKER
        )

    fun main(
        authProviders: List<JWTAuthentication> = defaultAuthProviders,
        altinn: Altinn = AltinnImpl,
        n√¶rmesteLederService: N√¶rmesteLederService = N√¶rmesteLederServiceImpl(),
        enhetsregisteret: Enhetsregisteret = EnhetsregisteretImpl(),
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val brukerModelAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    BrukerModelImpl(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            launch {
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer {
                        put(ConsumerConfig.GROUP_ID_CONFIG, "bruker-model-builder")
                    }
                    val queryModel = brukerModelAsync.await()

                    kafkaConsumer.forEachEvent { event ->
                        queryModel.oppdaterModellEtterHendelse(event)
                    }
                }
            }

            val graphql = async {
                BrukerAPI.createBrukerGraphQL(
                    altinn = altinn,
                    enhetsregisteret = enhetsregisteret,
                    brukerModel = brukerModelAsync.await(),
                    kafkaProducer = createKafkaProducer(),
                    n√¶rmesteLederService = n√¶rmesteLederService
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
                        extractContext = extractBrukerContext,
                        graphql = graphql,
                    )
                }
                httpServer.start(wait = true)
            }
        }
    }
}
