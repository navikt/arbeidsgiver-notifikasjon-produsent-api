package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

object ReplayValidator {
    val log = logger()

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            launch {
                val kafkaConsumer = createKafkaConsumer("replay-validator")
                kafkaConsumer.seekToBeginningOnAssignment()
                kafkaConsumer.forEachEvent { _ ->
                    // noop. implicitly validated
                }
            }

            launchHttpServer(httpPort)
        }
    }
}
