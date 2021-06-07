package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ValueDeserializer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ValueSerializer
import java.time.OffsetDateTime
import java.util.*

private val uuid = UUID.fromString("70a4beb3-53b9-49f0-ae31-2d5e6cfe52bf")

class KafkaSerializerTests : DescribeSpec({
    describe("kafka value serde") {
        val serializer = ValueSerializer()
        val deserializer = ValueDeserializer()

        val virksomhetsnummer = "123456789"

        context("BeskjedOpprettet") {
            val b = Hendelse.BeskjedOpprettet(
                tekst = "hallo",
                merkelapp = "merkelappen",
                id = uuid,
                mottaker = AltinnMottaker(
                    serviceCode = "1234",
                    serviceEdition = "1",
                    virksomhetsnummer = virksomhetsnummer
                ),
                lenke = "https://foop.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T00:00:00.00Z"),
                eksternId = "ekstern 1234h",
                virksomhetsnummer = virksomhetsnummer
            )

            it("serde preservers all values") {
                val serialized = serializer.serialize("", b)
                val deserialized = deserializer.deserialize("", serialized)
                deserialized shouldBe b
            }
        }
    }
})