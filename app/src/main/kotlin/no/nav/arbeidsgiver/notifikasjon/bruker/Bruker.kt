package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.async
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.enhetsregisterFactory
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractBrukerContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.graphqlSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent

object Bruker {
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
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig)
            val brukerRepositoryDeferred = async {
                BrukerRepositoryImpl(databaseDeferred.await())
            }

            val graphql = async {
                BrukerAPI.createBrukerGraphQL(
                    brukerRepository = brukerRepositoryDeferred.await(),
                    hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                    altinnTilgangerService = altinnTilgangerService,
                    virksomhetsinfoService = virksomhetsinfoService,
                )
            }

            graphqlSetup(
                authPluginConfig = HttpAuthProviders.BRUKER_API_AUTH,
                extractContext = extractBrukerContext,
                graphql = graphql
            )
        }.start(wait = true)
    }
}
