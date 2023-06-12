package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.server.metrics.micrometer.*
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.search.MeterNotFoundException
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.State
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
import java.io.File
import java.io.FileWriter
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

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

    private val coroutineGauges = State.values().associateWith {
        Metrics.meterRegistry.gauge(
            "kotlin.coroutines.count",
            Tags.of("state", it.name),
            AtomicInteger()
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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
        DebugProbes.enableCreationStackTraces = false
        DebugProbes.install()
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            launchProcessingLoop("debug coroutines", pauseAfterEach = Duration.ofMinutes(1)) {
                DebugProbes.dumpCoroutinesInfo().groupBy { it.state }.forEach { (state, coroutines) ->
                    coroutineGauges[state]?.set(coroutines.size)
                }
            }

            launchProcessingLoop("debug threads", pauseAfterEach = Duration.ofMinutes(10)) {
                val allThreads = Thread.getAllStackTraces()
                val stringBuilder = StringBuilder()
                for ((thread, stackTrace) in allThreads) {
                    stringBuilder.append("Thread ${thread.name} (state=${thread.state}):\n")
                    for (line in stackTrace) {
                        stringBuilder.append(" $line\n")
                    }
                    stringBuilder.append("\n")
                }
                FileWriter("/tmp/threads.txt", false).use { file ->
                    file.write(stringBuilder.toString())
                }
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
                val metricName = MicrometerMetricsConfig().metricName
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
