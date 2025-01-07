package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration
import java.time.Instant

object SkedulertHardDelete {
    val databaseConfig = Database.config("skedulert_harddelete_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-harddelete-model-builder-1",
            replayPeriodically = true,
        )
    }

    private val rebuildHardDeleted by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-harddelete-model-rebuild-07.01.2025",
        )
    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)

            val repoAsync = async {
                SkedulertHardDeleteRepositoryImpl(database.await())
            }
            launch {
                val repo = repoAsync.await()
                hendelsesstrøm.forEach { hendelse, metadata ->
                    repo.oppdaterModellEtterHendelse(hendelse, metadata.timestamp)
                }
            }
            launch {
                val repo = repoAsync.await()
                rebuildHardDeleted.forEach { hendelse, metadata ->
                    when (hendelse) {
                        is HendelseModel.SoftDelete,
                        is HendelseModel.HardDelete -> {
                            repo.delete(
                                aggregateId = hendelse.aggregateId,
                                merkelapp = when (hendelse) {
                                    is HendelseModel.HardDelete -> hendelse.merkelapp
                                    is HendelseModel.SoftDelete -> hendelse.merkelapp
                                    else -> throw IllegalStateException("unexpected event type")
                                },
                                grupperingsid = when (hendelse) {
                                    is HendelseModel.HardDelete -> hendelse.grupperingsid
                                    is HendelseModel.SoftDelete -> null
                                    else -> throw IllegalStateException("unexpected event type")
                                }
                            )
                        }

                        else -> Unit
                    }
                }
            }

            val service = async {
                SkedulertHardDeleteService(repoAsync.await(), lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC))
            }
            launchProcessingLoop(
                "autoslett-service",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                service.await().sendSkedulerteHardDeletes(Instant.now())
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
