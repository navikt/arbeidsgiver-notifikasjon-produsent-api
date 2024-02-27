package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.localKafka
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@Isolate
class KafkaHendelsesstrømTests: DescribeSpec({
    val localKafka = localKafka()

    val stop = AtomicBoolean(false)
    val sent = mutableSetOf<UUID>()
    val received = mutableSetOf<UUID>()
    val receivedHendelse = mutableSetOf<Hendelse>()

    describe("reading and writing from kafka") {
        val kafkaProducer = localKafka.newProducer()
        val hendelsesstrøm = localKafka.newConsumer()
        withData(EksempelHendelse.Alle) {
            kafkaProducer.send(it)
            sent.add(it.hendelseId)
        }

        hendelsesstrøm.forEach(stop) { hendelse ->
            receivedHendelse.add(hendelse)
            received.add(hendelse.hendelseId)
            if (sent == received || (received - sent).isNotEmpty()) {
                stop.set(true)
            }
        }

        it("should have exactly the same ids and events we sent") {
            received shouldBe sent
            receivedHendelse shouldContainExactlyInAnyOrder EksempelHendelse.Alle
        }
    }
})