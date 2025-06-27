package no.nav.arbeidsgiver.notifikasjon.bruker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.enhetsregisterFactory
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchGraphqlServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

object Bruker {
    private val log = logger()
    val databaseConfig = Database.config("bruker_model")

    fun main(
        enhetsregisteret: Enhetsregisteret = enhetsregisterFactory(),
        virksomhetsinfoService: VirksomhetsinfoService = VirksomhetsinfoService(enhetsregisteret),
        altinnTilgangerService: AltinnTilgangerService = AltinnTilgangerServiceImpl(
            altinnTilgangerClient = AltinnTilgangerClient(
                observer = virksomhetsinfoService::cachePut,
            )
        ),
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
        }
    }
}
