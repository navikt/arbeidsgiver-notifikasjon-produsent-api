package no.nav.arbeidsgiver.notifikasjon

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractProdusentContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.httpServerSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.PRODUSENT_REGISTER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.time.Duration

object Produsent {
    val log = logger()
    val databaseConfig = Database.config("produsent_model")

    private val defaultAuthProviders = when (val name = System.getenv("NAIS_CLUSTER_NAME")) {
        "prod-gcp" -> listOf(
            HttpAuthProviders.AZURE_AD,
        )
        "dev-gcp" -> listOf(
            HttpAuthProviders.AZURE_AD,
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
        )
        null -> listOf(
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
        )
        else -> {
            val msg = "ukjent NAIS_CLUSTER_NAME '$name'"
            log.error(msg)
            throw Error(msg)
        }
    }

    fun main(
        authProviders: List<JWTAuthentication> = defaultAuthProviders,
        httpPort: Int = 8080,
        produsentRegister: ProdusentRegister = PRODUSENT_REGISTER,
        altinn: Altinn = AltinnImpl(),
    ) {
        runBlocking(Dispatchers.Default) {
            val produsentRepositoryAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    ProdusentRepositoryImpl(database)
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
                        put(ConsumerConfig.GROUP_ID_CONFIG, "produsent-model-builder")
                    }
                    val produsentRepository = produsentRepositoryAsync.await()

                    kafkaConsumer.forEachEvent { event ->
                        produsentRepository.oppdaterModellEtterHendelse(event)
                    }
                }
            }

            val graphql = async {
                ProdusentAPI.newGraphQL(
                    kafkaProducer = createKafkaProducer(),
                    produsentRepository = produsentRepositoryAsync.await(),
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
                        extractContext = extractProdusentContext(produsentRegister),
                        graphql = graphql
                    )
                }
                httpServer.start(wait = true)
            }
            launch {
                launchProcessingLoop(
                    "last Altinnroller",
                    pauseAfterEach = Duration.ofDays(1),
                ) {
                    AltinnRolleServiceImpl(altinn, produsentRepositoryAsync.await().altinnRolle).lastRollerFraAltinn()
                }
            }
        }
    }
}