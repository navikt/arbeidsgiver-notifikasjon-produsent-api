package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.BeskjedOpprettet
import java.util.*

private val guid = UUID.fromString("70a4beb3-53b9-49f0-ae31-2d5e6cfe52bf")

class KafkaSerializerTests : DescribeSpec({
    describe("kafka value serde") {
        val serializer = ValueSerializer()
        val deserializer = ValueDeserializer()

        context("BeskjedOpprettet") {
            val b = BeskjedOpprettet(
                tekst = "hallo",
                merkelapp = "merkelappen",
                guid = guid,
                mottaker = AltinnMottaker(
                    altinntjenesteKode = "1234",
                    altinntjenesteVersjon = "1",
                    virksomhetsnummer = "123456789"
                ),
                lenke = "https://foop.no",
                opprettetTidspunkt = "20200101T121212",
                eksternId= "ekstern 1234h"
            )

            it("serde to itself") {
                val serialized = serializer.serialize("", b)
                val deserialized = deserializer.deserialize("", serialized)
                deserialized shouldBe b
            }
        }
    }
})