package no.nav.arbeidsgiver.notifikasjon.produsent

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAndSetReady
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractProdusentContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.graphqlSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.PRODUSENT_REGISTER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI

object Produsent {
    val databaseConfig = Database.config("produsent_model")

    fun main(
        httpPort: Int = 8080,
        produsentRegister: ProdusentRegister = PRODUSENT_REGISTER,
    ) {

        embeddedServer(CIO, port = httpPort) {
            val database = openDatabaseAndSetReady(databaseConfig)
            val produsentRepository = ProdusentRepositoryImpl(database)

            val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
            val graphql = ProdusentAPI.newGraphQL(
                kafkaProducer = hendelseProdusent,
                produsentRepository = produsentRepository,
            )

            val hendelsesstrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "produsent-model-builder",
                replayPeriodically = true
            )

            launch {
                hendelsesstrøm.forEach { event, metadata ->
                    produsentRepository.oppdaterModellEtterHendelse(event, metadata)
                }
            }

            graphqlSetup(
                authPluginConfig = HttpAuthProviders.PRODUSENT_API_AUTH,
                extractContext = extractProdusentContext(produsentRegister),
                graphql = graphql
            )
            registerShutdownListener()
        }.start(wait = true)
    }
}