package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueDeserializer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.ValueSerializer
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse


class KafkaSerializerTests : DescribeSpec({
    describe("kafka value serde") {
        val serializer = ValueSerializer()
        val deserializer = ValueDeserializer()

        withData(EksempelHendelse.Alle) { hendelse ->
            val serialized = serializer.serialize("", hendelse)
            val deserialized = deserializer.deserialize("", serialized)
            deserialized shouldBe hendelse
        }
    }

    describe("korrupt hendelse i dev-gcp") {
        val deserializer = ValueDeserializer()

        /**
         * pga en feil i test i dev ble det laget en korrupt hendelse. Dette er kode som rydder opp. Kan slettes når
         * hendelsen er tombstonet og compacted.
         */
        it("skips corrupt event in dev-gcp") {
            val serialized = """
                {
                  "@type": "PaaminnelseOpprettet",
                  "virksomhetsnummer": "910825526",
                  "hendelseId": "37f5df5e-c8a2-42ca-80c4-377eb57d8c5f",
                  "produsentId": "fager",
                  "kildeAppNavn": "dev-gcp:fager:notifikasjon-skedulert-paaminnelse",
                  "notifikasjonId": "de8ac3c3-4f51-41e5-bcc1-455fcf1cee7e",
                  "bestillingHendelseId": "de8ac3c3-4f51-41e5-bcc1-455fcf1cee7e",
                  "opprettetTidpunkt": "2024-03-22T12:01:57.465888822Z",
                  "fristOpprettetTidspunkt": "2024-03-22T11:44:03.941061085Z",
                  "frist": null,
                  "tidspunkt": {
                    "@type": "PaaminnelseTidspunkt.FoerStartTidspunkt",
                    "foerStartTidspunkt": "PT5M",
                    "paaminnelseTidspunkt": "2024-03-22T11:47:10.983Z"
                  },
                  "eksterneVarsler": [
                    {
                      "@type": "LinkedHashMap",
                      "varselId": "bee8f018-5d30-4293-a2e5-a8f2aa00a1a6",
                      "epostAddr": "ken.gullaksen@nav.no",
                      "fnrEllerOrgnr": "910825526",
                      "tittel": "Varsel ved påminnelse fra testpodusent",
                      "htmlBody": "<h1>Hei</h1><p>Dette er en påminnelse</p>",
                      "sendevindu": "LØPENDE",
                      "sendeTidspunkt": null
                    }
                  ]
                }
            """.trimIndent().toByteArray()
            val deserialized = deserializer.deserialize("", serialized)
            deserialized shouldBe null
        }
    }
})