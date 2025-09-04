package no.nav.arbeidsgiver.notifikasjon.produsent

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractProdusentContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.graphqlSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.PRODUSENT_REGISTER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI

object Produsent {
    val databaseConfig = Database.config("produsent_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "produsent-model-builder",
            replayPeriodically = true
        )
    }

    fun main(
        httpPort: Int = 8080,
        produsentRegister: ProdusentRegister = PRODUSENT_REGISTER,
    ) {
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig)
            val produsentRepositoryDeferred = async {
                ProdusentRepositoryImpl(databaseDeferred.await())
            }

            launch {
                val produsentRepository = produsentRepositoryDeferred.await()
                hendelsesstrøm.forEach { event, metadata ->
                    produsentRepository.oppdaterModellEtterHendelse(event, metadata)
                }
            }

            val graphqlDeferred = async {
                ProdusentAPI.newGraphQL(
                    kafkaProducer = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                    produsentRepository = produsentRepositoryDeferred.await(),
                )
            }

            graphqlSetup(
                authPluginConfig = HttpAuthProviders.PRODUSENT_API_AUTH,
                extractContext = extractProdusentContext(produsentRegister),
                graphql = graphqlDeferred
            )
        }.start(wait = true)
    }
}