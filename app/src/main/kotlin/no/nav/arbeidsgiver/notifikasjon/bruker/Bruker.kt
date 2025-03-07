package no.nav.arbeidsgiver.notifikasjon.bruker

import io.micrometer.core.instrument.search.MeterNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import java.time.Duration

object Bruker {
    private val log = logger()
    val databaseConfig = Database.config(
        "bruker_model",
        jdbcOpts = basedOnEnv(
            prod = {
                mapOf(
                    "socketFactory" to "com.google.cloud.sql.postgres.SocketFactory",
                    "cloudSqlInstance" to System.getenv("CLOUD_SQL_INSTANCE")!!
                )
            },
            other = { emptyMap() }
        )
    )

    fun main(
        enhetsregisteret: Enhetsregisteret = enhetsregisterFactory(),
        virksomhetsinfoService: VirksomhetsinfoService = VirksomhetsinfoService(enhetsregisteret),
        altinnTilgangerService: AltinnTilgangerService = AltinnTilgangerServiceImpl(AltinnTilgangerClient(
            observer = virksomhetsinfoService::cachePut,
        )),
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            val graphql = async {
                BrukerAPI.createBrukerGraphQL(
                    brukerRepository = brukerRepositoryAsync.await(),
                    hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                    altinnTilgangerService = altinnTilgangerService,
                    virksomhetsinfoService = virksomhetsinfoService,
                )
            }

            launchGraphqlServer(
                httpPort = httpPort,
                authPluginConfig = HttpAuthProviders.BRUKER_API_AUTH,
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
