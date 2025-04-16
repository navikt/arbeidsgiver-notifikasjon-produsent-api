package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.localKafka
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class KafkaHendelsesstrømTest {
    companion object {
        @JvmField
        @RegisterExtension
        val localKafka = localKafka()
    }

    private val stop = AtomicBoolean(false)
    private val sent = mutableSetOf<UUID>()
    private val received = mutableSetOf<UUID>()
    private val receivedHendelse = mutableSetOf<Hendelse>()

    @Test
    fun `reading and writing from kafka`() = runBlocking {
        val kafkaProducer = localKafka.newProducer()
        val hendelsesstrøm = localKafka.newConsumer()
        EksempelHendelse.Alle.forEach {
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

        // should have exactly the same ids and events we sent
        assertEquals(sent, received)
        assertEquals(EksempelHendelse.Alle, receivedHendelse.toList())
    }
}