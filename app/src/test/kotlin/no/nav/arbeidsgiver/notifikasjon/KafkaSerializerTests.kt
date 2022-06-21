package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueDeserializer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueSerializer
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse


class KafkaSerializerTests : DescribeSpec({
    describe("kafka value serde") {
        val serializer = ValueSerializer()
        val deserializer = ValueDeserializer()

        forAll<HendelseModel.Hendelse>(EksempelHendelse.Alle) { hendelse ->
            it("serde preservers all values") {
                val serialized = serializer.serialize("", hendelse)
                val deserialized = deserializer.deserialize("", serialized)
                deserialized shouldBe hendelse
            }
        }
    }
})