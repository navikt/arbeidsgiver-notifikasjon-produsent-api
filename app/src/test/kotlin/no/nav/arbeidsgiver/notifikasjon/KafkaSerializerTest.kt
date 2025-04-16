package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueDeserializer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueSerializer
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import kotlin.test.Test
import kotlin.test.assertEquals


class KafkaSerializerTest {
    @Test
    fun `kafka value serde`() {
        val serializer = ValueSerializer()
        val deserializer = ValueDeserializer()

        EksempelHendelse.Alle.forEach { hendelse ->
            val serialized = serializer.serialize("", hendelse)
            val deserialized = deserializer.deserialize("", serialized)
            assertEquals(hendelse, deserialized)
        }
    }
}