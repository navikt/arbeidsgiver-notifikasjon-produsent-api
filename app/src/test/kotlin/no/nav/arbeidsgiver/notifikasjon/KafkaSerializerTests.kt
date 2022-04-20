package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueDeserializer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueSerializer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*


class KafkaSerializerTests : DescribeSpec({
    val uuid = UUID.fromString("70a4beb3-53b9-49f0-ae31-2d5e6cfe52bf")

    describe("kafka value serde") {
        val serializer = ValueSerializer()
        val deserializer = ValueDeserializer()

        val virksomhetsnummer = "123456789"

        context("BeskjedOpprettet") {
            val b = BeskjedOpprettet(
                tekst = "hallo",
                merkelapp = "merkelappen",
                hendelseId = uuid,
                notifikasjonId = uuid,
                mottakere = listOf(AltinnMottaker(
                    serviceCode = "1234",
                    serviceEdition = "1",
                    virksomhetsnummer = virksomhetsnummer
                )),
                lenke = "https://foop.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T00:00:00.00Z"),
                eksternId = "ekstern 1234h",
                virksomhetsnummer = virksomhetsnummer,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52"))
            )

            it("serde preservers all values") {
                val serialized = serializer.serialize("", b)
                val deserialized = deserializer.deserialize("", serialized)
                deserialized shouldBe b
            }
        }
    }
})