package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClient
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClientImpl
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractProdusentContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.PRODUSENT_REGISTER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import java.time.Duration

object Produsent {
    val databaseConfig = Database.config("produsent_model")
    private val log = logger()
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "produsent-model-builder",
            replayPeriodically = true
        )
    }

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
        altinnRolleClient: AltinnRolleClient = AltinnRolleClientImpl(),
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val produsentRepositoryAsync = async {
                ProdusentRepositoryImpl(database.await())
            }

            launch {
                val produsentRepository = produsentRepositoryAsync.await()
                hendelsesstrøm.forEach { event ->
                    produsentRepository.oppdaterModellEtterHendelse(event)
                }
            }

            val graphql = async {
                ProdusentAPI.newGraphQL(
                    kafkaProducer = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                    produsentRepository = produsentRepositoryAsync.await(),
                )
            }

            launchGraphqlServer(
                httpPort = httpPort,
                authProviders = authProviders,
                extractContext = extractProdusentContext(produsentRegister),
                graphql = graphql
            )

            launchProcessingLoop(
                "cleanup duplicate mottakere nærmeste leder",
                pauseAfterEach = Duration.ofMillis(200)
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
                pauseAfterEach = Duration.ofMillis(200)
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

            launch {
                val altinnRolleService = AltinnRolleServiceImpl(
                    altinnRolleClient,
                    produsentRepositoryAsync.await().altinnRolle
                )
                launchProcessingLoop(
                    "last Altinnroller",
                    pauseAfterEach = Duration.ofDays(1),
                ) {
                    altinnRolleService.lastRollerFraAltinn()
                }
            }
        }
    }
}