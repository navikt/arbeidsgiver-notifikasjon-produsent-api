package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnRolleClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnRolleClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractProdusentContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.PRODUSENT_REGISTER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
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
        altinnRolleClient: AltinnRolleClient = AltinnRolleClientImpl(),
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(Health.database, databaseConfig)

            val produsentRepositoryAsync = async {
                ProdusentRepositoryImpl(database.await())
            }

            launch {
                val produsentRepository = produsentRepositoryAsync.await()
                forEachHendelse("produsent-model-builder") { event ->
                    produsentRepository.oppdaterModellEtterHendelse(event)
                }
            }

            val graphql = async {
                ProdusentAPI.newGraphQL(
                    kafkaProducer = createKafkaProducer(),
                    produsentRepository = produsentRepositoryAsync.await(),
                )
            }

            launchGraphqlServer(
                httpPort = httpPort,
                authProviders = authProviders,
                extractContext = extractProdusentContext(produsentRegister),
                graphql = graphql
            )

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