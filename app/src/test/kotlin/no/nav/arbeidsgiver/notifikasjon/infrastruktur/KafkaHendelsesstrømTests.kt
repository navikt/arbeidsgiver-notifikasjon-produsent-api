package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class KafkaHendelsesstrømTests: DescribeSpec({
    val embeddedKafka = embeddedKafka()
    val kafkaProducer = embeddedKafka.newProducer()
    val hendelsesstrøm = embeddedKafka.newConsumer()

    val stop = AtomicBoolean(false)
    val sent = mutableSetOf<UUID>()
    val received = mutableSetOf<UUID>()
    val receivedHendelse = mutableSetOf<Hendelse>()

    describe("reading and writing from kafka") {
        forAll<Hendelse>(EksempelHendelse.Alle) {
            kafkaProducer.send(it)
            sent.add(it.hendelseId)
        }

        hendelsesstrøm.forEach(stop) { hendelse ->
            receivedHendelse.add(hendelse)
            received.add(hendelse.hendelseId)
            if (sent == received || (received - sent).isNotEmpty() ) {
                stop.set(true)
            }
        }

        it("should have exactly the same ids and events we sent") {
            sent shouldBe received
            EksempelHendelse.Alle shouldContainExactlyInAnyOrder receivedHendelse
        }
    }
})