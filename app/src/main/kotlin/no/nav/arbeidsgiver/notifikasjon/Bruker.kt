package no.nav.arbeidsgiver.notifikasjon

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleService
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.NarmesteLederLeesahDeserializer
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModelImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.TilgangerServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.httpServerSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createAndSubscribeKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.time.Duration


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

    private val defaultAuthProviders = when (val name = System.getenv("NAIS_CLUSTER_NAME")) {
        "prod-gcp" -> listOf(
            HttpAuthProviders.LOGIN_SERVICE,
            HttpAuthProviders.TOKEN_X,
        )
        null, "dev-gcp" -> listOf(
            HttpAuthProviders.LOGIN_SERVICE,
            HttpAuthProviders.TOKEN_X,
            HttpAuthProviders.FAKEDINGS_BRUKER
        )
        else -> {
            val msg = "ukjent NAIS_CLUSTER_NAME '$name'"
            log.error(msg)
            throw Error(msg)
        }
    }

    fun main(
        authProviders: List<JWTAuthentication> = defaultAuthProviders,
        altinn: Altinn = AltinnImpl(),
        enhetsregisteret: Enhetsregisteret = enhetsregisterFactory(),
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val brukerRepositoryAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    BrukerRepositoryImpl(database)
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
                    val brukerRepository = brukerRepositoryAsync.await()

                    kafkaConsumer.forEachEvent { event ->
                        brukerRepository.oppdaterModellEtterHendelse(event)
                    }
                }
            }

            val nærmesteLederModelAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    NærmesteLederModelImpl(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            val altinnRolleService = async<AltinnRolleService> {
                AltinnRolleServiceImpl(altinn, brukerRepositoryAsync.await().altinnRolle)
            }

            launch {
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val nærmesteLederLeesahTopic = "teamsykmelding.syfo-narmesteleder-leesah"
                    val nærmesteLederKafkaConsumer =
                        createAndSubscribeKafkaConsumer<String, NarmesteLederLeesah>(nærmesteLederLeesahTopic) {
                            putAll(
                                mapOf(
                                    ConsumerConfig.GROUP_ID_CONFIG to "notifikasjon-bruker-api-narmesteleder-model-builder",
                                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to NarmesteLederLeesahDeserializer::class.java.canonicalName,
                                )
                            )
                        }
                    val nærmesteLederModel = nærmesteLederModelAsync.await()

                    nærmesteLederKafkaConsumer.forEachEvent { event ->
                        nærmesteLederModel.oppdaterModell(event)
                    }
                }
            }

            val graphql = async {
                val tilgangerService = TilgangerServiceImpl(
                    altinn = altinn,
                    altinnRolleService = altinnRolleService.await(),
                )
                BrukerAPI.createBrukerGraphQL(
                    enhetsregisteret = enhetsregisteret,
                    brukerRepository = brukerRepositoryAsync.await(),
                    kafkaProducer = createKafkaProducer(),
                    tilgangerService = tilgangerService,
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

            launch {
                launchProcessingLoop(
                    "last Altinnroller",
                    pauseAfterEach = Duration.ofDays(1),
                ) {
                    altinnRolleService.await().lastRollerFraAltinn()
                }
            }
        }
    }
}
