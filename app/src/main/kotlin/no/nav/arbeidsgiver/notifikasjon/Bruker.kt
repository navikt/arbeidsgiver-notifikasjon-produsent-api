package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClient
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClientImpl
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleService
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.NarmesteLederLeesahDeserializer
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModelImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.TilgangerServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.enhetsregisterFactory
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createAndSubscribeKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.time.Duration


object Bruker {
    val log = logger()
    val databaseConfig = Database.config("bruker_model")

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
        altinnRolleClient: AltinnRolleClient = AltinnRolleClientImpl(),
        altinn: Altinn = AltinnImpl(),
        enhetsregisteret: Enhetsregisteret = enhetsregisterFactory(),
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            launch {
                val brukerRepository = brukerRepositoryAsync.await()
                forEachHendelse("bruker-model-builder") { event ->
                    brukerRepository.oppdaterModellEtterHendelse(event)
                }
            }

            val nærmesteLederModelAsync = async {
                NærmesteLederModelImpl(database.await())
            }

            val altinnRolleService = async<AltinnRolleService> {
                AltinnRolleServiceImpl(altinnRolleClient, brukerRepositoryAsync.await().altinnRolle)
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

            launchGraphqlServer(
                httpPort = httpPort,
                authProviders = authProviders,
                extractContext = extractBrukerContext,
                graphql = graphql,
            )

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
