package no.nav.arbeidsgiver.notifikasjon.bruker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NærmesteLederKafkaListener
import java.util.*

object BrukerWriter {
    val databaseConfig = Database.config(
        "bruker_model",
        envPrefix = "DB_BRUKER_API_KAFKA_USER",
        jdbcOpts = mapOf(
            "socketFactory" to "com.google.cloud.sql.postgres.SocketFactory",
            "cloudSqlInstance" to System.getenv("CLOUD_SQL_INSTANCE")!!
        )
    )

    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "bruker-model-builder-2",
            replayPeriodically = true,
        )
    }

    private val rebuildAlltinnTilganger by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "bruker-model-rebuild-07.11.2024-3",
        )
    }

    fun main(
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            launch {
                val brukerRepository = brukerRepositoryAsync.await()
                hendelsesstrøm.forEach { event, metadata ->
                    brukerRepository.oppdaterModellEtterHendelse(event, metadata)
                }
            }

            launch {
                val db = database.await()
                rebuildAlltinnTilganger.forEach { event ->
                    when (event) {
                        is HendelseModel.BeskjedOpprettet -> {
                            event.mottakere
                                .filterIsInstance<HendelseModel.AltinnMottaker>()
                                .forEach { mottaker ->
                                    db.transaction {
                                        lagreMottaker(event.notifikasjonId, null, mottaker)
                                    }
                                }
                        }

                        is HendelseModel.OppgaveOpprettet -> {
                            event.mottakere
                                .filterIsInstance<HendelseModel.AltinnMottaker>()
                                .forEach { mottaker ->
                                    db.transaction {
                                        lagreMottaker(event.notifikasjonId, null, mottaker)
                                    }
                                }
                        }

                        is HendelseModel.KalenderavtaleOpprettet -> {
                            event.mottakere
                                .filterIsInstance<HendelseModel.AltinnMottaker>()
                                .forEach { mottaker ->
                                    db.transaction {
                                        lagreMottaker(event.notifikasjonId, null, mottaker)
                                    }
                                }
                        }

                        is HendelseModel.SakOpprettet -> {
                            event.mottakere
                                .filterIsInstance<HendelseModel.AltinnMottaker>()
                                .forEach { mottaker ->
                                    db.transaction {
                                        lagreMottaker(null, event.sakId, mottaker)
                                    }
                                }

                        }

                        else -> Unit
                    }
                }
            }

            launch {
                val brukerRepository = brukerRepositoryAsync.await()
                NærmesteLederKafkaListener().forEach { event ->
                    brukerRepository.oppdaterModellEtterNærmesteLederLeesah(event)
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }

    private fun Transaction.lagreMottaker(
        notifikasjonId: UUID?,
        sakId: UUID?,
        mottaker: HendelseModel.AltinnMottaker
    ) {
        executeUpdate(
            """
                insert into mottaker_altinn_tilgang
                    (notifikasjon_id, sak_id, virksomhet, altinn_tilgang)
                select ?, ?, ?, ?
                    where exists (select 1 from notifikasjon where id = ?)
                    or exists (select 1 from sak where id = ?)
                on conflict do nothing
            """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            text(mottaker.virksomhetsnummer)
            text("${mottaker.serviceCode}:${mottaker.serviceEdition}")

            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
        }
    }
}
