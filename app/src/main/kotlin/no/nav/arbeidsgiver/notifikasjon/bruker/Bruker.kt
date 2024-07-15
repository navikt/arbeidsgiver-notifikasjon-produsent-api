package no.nav.arbeidsgiver.notifikasjon.bruker

import io.micrometer.core.instrument.search.MeterNotFoundException
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnCachedImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.SuspendingAltinnClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import java.time.Duration

object Bruker {
    private val log = logger()
    val databaseConfig = Database.config("bruker_model")

    private val defaultAuthProviders = when (val name = System.getenv("NAIS_CLUSTER_NAME")) {
        "prod-gcp" -> listOf(
            HttpAuthProviders.TOKEN_X,
        )
        null, "dev-gcp" -> listOf(
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
        enhetsregisteret: Enhetsregisteret = enhetsregisterFactory(),
        virksomhetsinfoService: VirksomhetsinfoService = VirksomhetsinfoService(enhetsregisteret),
        suspendingAltinnClient: SuspendingAltinnClient = SuspendingAltinnClient(
            observer = virksomhetsinfoService::altinnObserver
        ),
        altinn: Altinn = AltinnCachedImpl(suspendingAltinnClient),
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            val graphql = async {
                val tilgangerService = TilgangerServiceImpl(
                    altinn = altinn
                )
                BrukerAPI.createBrukerGraphQL(
                    brukerRepository = brukerRepositoryAsync.await(),
                    hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                    tilgangerService = tilgangerService,
                    virksomhetsinfoService = virksomhetsinfoService,
                )
            }

            launchGraphqlServer(
                httpPort = httpPort,
                authProviders = authProviders,
                extractContext = extractBrukerContext,
                graphql = graphql,
            )

            launchProcessingLoop(
                "sjekk aktive ktor connections",
                pauseAfterEach = Duration.ofSeconds(60),
                init = { delay(Duration.ofSeconds(60).toMillis()) }
            ) {

                val maxThreshold = 1000

                // Equal to [MicrometerMetricsConfig().metricName]. But doing that
                // creates a new thread on every invocation, which causes the number
                // of threads to increase over time.
                val metricName = "ktor.http.server.requests"

                val activeConnections : Double = try {
                    Metrics.meterRegistry.get("$metricName.active").gauge().value()
                } catch (e: MeterNotFoundException) {
                    log.warn("ktor activeConnections count not available", e)
                    0.0
                }

                if (activeConnections > maxThreshold) {
                    log.warn("ktor activeConnections $activeConnections is over threshold $maxThreshold")
                    Health.subsystemAlive[Subsystem.KTOR] = false
                }
            }
        }
    }
}
