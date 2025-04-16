package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.test.Test
import kotlin.test.assertFalse

class CoroutineKafkaConsumerKtTest {

    @Test
    fun `ConsumerRecord#toLoggableString`() {
        listOf(
            *EksempelHendelse.Alle.toTypedArray(),
            null
        ).forEach { value ->
            val loggableString = ConsumerRecord("foo.topic", 0, 0, "somekey", value).loggableToString()
            assertFalse(loggableString.isEmpty())
        }
    }
}
