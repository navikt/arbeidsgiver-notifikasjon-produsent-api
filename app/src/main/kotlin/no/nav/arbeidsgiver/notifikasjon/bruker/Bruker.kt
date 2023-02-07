package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClient
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClientImpl
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleService
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnCachedImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.SuspendingAltinnClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NærmesteLederKafkaListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import java.time.Duration

object Bruker {
    private val log = logger()
    val databaseConfig = Database.config("bruker_model")

    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = basedOnEnv(
                dev = { "bruker-model-builder-1" },
                other = { "bruker-model-builder" },
            ),
            replayPeriodically = true,
        )
    }

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
        altinnRolleClient: AltinnRolleClient = AltinnRolleClientImpl(),
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

//            launch {
//                val brukerRepository = brukerRepositoryAsync.await()
//                hendelsesstrøm.forEach { event ->
//                    brukerRepository.oppdaterModellEtterHendelse(event)
//                }
//            }

            launch {
                val brukerRepository = brukerRepositoryAsync.await()
                NærmesteLederKafkaListener().forEach { event ->
                    brukerRepository.oppdaterModellEtterNærmesteLederLeesah(event)
                }
            }

            val altinnRolleService = async<AltinnRolleService> {
                AltinnRolleServiceImpl(altinnRolleClient, brukerRepositoryAsync.await().altinnRolle)
            }

            val graphql = async {
                val tilgangerService = TilgangerServiceImpl(
                    altinn = altinn,
                    altinnRolleService = altinnRolleService.await(),
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
                "last Altinnroller",
                pauseAfterEach = Duration.ofDays(1),
            ) {
                altinnRolleService.await().lastRollerFraAltinn()
            }

            launchProcessingLoop(
                "cleanup duplicate mottakere nærmeste leder",
                pauseAfterEach = Duration.ofSeconds(2)
            ) {
                val db = database.await()

                val duplikateIder = db.nonTransactionalExecuteQuery(
                    """
                        select jsonb_agg(id) as idr
                        from mottaker_digisyfo
                        group by notifikasjon_id
                        having count(*) > 1
                        limit 100
                    """
                ) {
                    laxObjectMapper.readValue<List<Long>>(getString("idr"))
                }

                val resultat = db.nonTransactionalExecuteBatch("""
                    delete from mottaker_digisyfo
                    where id = ?
                """,
                    duplikateIder.flatMap { it.sorted().drop(1) }.take(10_000)
                ) {
                    long(it)
                }

                if (duplikateIder.isNotEmpty()) {
                    log.info("fant ${duplikateIder.size} digisyfo-mottakere med duplikater. slettet ${resultat.sum()} rader av ${resultat.size} forsøkte")
                }
            }

            launchProcessingLoop(
                "cleanup duplicate mottakere altinn",
                pauseAfterEach = Duration.ofSeconds(2)
            ) {
                val db = database.await()

                val duplikateIder = db.nonTransactionalExecuteQuery(
                    """
                        select jsonb_agg(id) as idr
                        from mottaker_altinn_enkeltrettighet
                        group by notifikasjon_id
                        having count(*) > 1
                        limit 100
                    """
                ) {
                    laxObjectMapper.readValue<List<Long>>(getString("idr"))
                }

                val resultat = db.nonTransactionalExecuteBatch("""
                    delete from mottaker_altinn_enkeltrettighet
                    where id = ?
                """,
                    duplikateIder.flatMap { it.sorted().drop(1) }.take(10_000)
                ) {
                    long(it)
                }

                if (duplikateIder.isNotEmpty()) {
                    log.info("fant ${duplikateIder.size} altinn-mottakere med duplikater. slettet ${resultat.sum()} rader av ${resultat.size} forsøkte")
                }
            }
        }
    }
}
