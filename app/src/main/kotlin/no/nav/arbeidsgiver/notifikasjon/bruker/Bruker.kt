package no.nav.arbeidsgiver.notifikasjon.bruker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.debug.CoroutinesBlockHoundIntegration
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceStub
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnCachedImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.SuspendingAltinnClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.enhetsregisterFactory
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import reactor.blockhound.BlockHound
import java.time.Duration

object Bruker {
    private val log = logger()
    val databaseConfig = Database.config("bruker_model")

//    private val hendelsesstrøm by lazy {
//        HendelsesstrømKafkaImpl(
//            topic = NOTIFIKASJON_TOPIC,
//            groupId = "bruker-model-builder-2",
//        )
//    }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun main(
        authProviders: List<JWTAuthentication> = defaultAuthProviders,
//        altinnRolleClient: AltinnRolleClient = AltinnRolleClientImpl(),
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
        BlockHound.builder()
            .with(CoroutinesBlockHoundIntegration())
            .blockingMethodCallback {
                log.warn("blocking call", Error(it.name))
            }
            .install()
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            launchProcessingLoop("debug coroutines", pauseAfterEach = Duration.ofMinutes(1)) {
                log.info("coroutines info: ${DebugProbes.dumpCoroutinesInfo()}")
            }

//            val altinnRolleService = async<AltinnRolleService> {
//                AltinnRolleServiceImpl(altinnRolleClient, brukerRepositoryAsync.await().altinnRolle)
//            }

            val graphql = async {
                val tilgangerService = TilgangerServiceImpl(
                    altinn = altinn,
                    altinnRolleService = AltinnRolleServiceStub() //altinnRolleService.await(),
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

//            launchProcessingLoop(
//                "last Altinnroller",
//                pauseAfterEach = Duration.ofDays(1),
//            ) {
//                altinnRolleService.await().lastRollerFraAltinn()
//            }
        }
    }
}
