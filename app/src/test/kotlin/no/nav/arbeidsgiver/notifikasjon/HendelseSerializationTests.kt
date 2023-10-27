package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.kafkaObjectMapper
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/** Unit tests for historical and current formats that
 * may be seen in kafka log.
 */
class HendelseSerializationTests : DescribeSpec({

    describe("Serialiserer av timestamps") {
        val oppgaveOpprettet = HardDelete(
            virksomhetsnummer = "",
            aggregateId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            deletedAt = OffsetDateTime.parse("2020-02-02T02:02+02"),
            grupperingsid = null,
            merkelapp = null,
        )

        it("OffsetDateTime serialiseres med offset") {
            val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
            json["deletedAt"].asText() shouldBe "2020-02-02T02:02:00+02:00"
        }


        val fristUtsatt = HendelseModel.FristUtsatt(
            virksomhetsnummer = "1",
            hendelseId = uuid("1"),
            produsentId = "produsent",
            kildeAppNavn = "app",
            notifikasjonId = uuid("2"),
            fristEndretTidspunkt= Instant.parse("2020-01-01T01:02:03Z"),
            frist = LocalDate.of(2020,1, 20),
            påminnelse = null,
        )
        it("Instant serialiseres med Z") {
            val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(fristUtsatt))
            json["fristEndretTidspunkt"].asText() shouldBe "2020-01-01T01:02:03Z"
        }

        it("LocalDate serialiseres som YYYY-MM-DD") {
            val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(fristUtsatt))
            json["frist"].asText() shouldBe "2020-01-20"
        }
    }

    describe("kun 'mottaker'") {
        val oppgaveOpprettet = OppgaveOpprettet(
            virksomhetsnummer = "",
            notifikasjonId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            merkelapp = "",
            eksternId = "",
            mottakere = listOf(AltinnMottaker(
                serviceCode = "1",
                serviceEdition = "2",
                virksomhetsnummer = "3",
            )),
            tekst = "",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2000-01-01T01:01+01"),
            eksterneVarsler = listOf(),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            frist = null,
            påminnelse = null,
            sakId = null,
        )


        it("mottaker parsed") {
            val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
            json.has("mottaker") shouldBe false
            json["mottakere"][0]["serviceCode"].asText() shouldBe "1"
            json["mottakere"][0]["serviceEdition"].asText() shouldBe "2"
            json["mottakere"][0]["virksomhetsnummer"].asText() shouldBe "3"
        }
    }

    /* Dette hadde vært fint å få den mer korrekte 'aggregateId' i json, så bare å slette
     * denne testen hvis det endres! */
    describe("har ikke 'aggregateId', men 'notifikasjonId' i HardDelete json") {
        val oppgaveOpprettet = HardDelete(
            virksomhetsnummer = "",
            aggregateId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            deletedAt = OffsetDateTime.parse("2020-02-02T02:02+02"),
            grupperingsid = null,
            merkelapp = null,
        )

        it("mottaker parsed") {
            val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
            json.has("aggregateId") shouldBe false
            json.has("notifikasjonId") shouldBe true
        }
    }

    /* Dette hadde vært fint å få den mer korrekte 'aggregateId' i json, så bare å slette
     * denne testen hvis det endres! */
    describe("har ikke 'aggregateId', men 'notifikasjonId' i SoftDelete json") {
        val oppgaveOpprettet = SoftDelete(
            virksomhetsnummer = "",
            aggregateId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            deletedAt = OffsetDateTime.parse("2020-02-02T02:02+02"),
            grupperingsid = null,
            merkelapp = null,
        )

        it("mottaker parsed") {
            val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
            json.has("aggregateId") shouldBe false
            json.has("notifikasjonId") shouldBe true
        }
    }

    describe("har ikke 'aggregateId' i json") {
        val oppgaveOpprettet = OppgaveOpprettet(
            virksomhetsnummer = "",
            notifikasjonId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            merkelapp = "",
            eksternId = "",
            mottakere = listOf(AltinnMottaker(
                serviceCode = "1",
                serviceEdition = "2",
                virksomhetsnummer = "3",
            )),
            tekst = "",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2000-01-01T01:01+01"),
            eksterneVarsler = listOf(),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            frist = null,
            påminnelse = null,
            sakId = null,
        )


        it("mottaker parsed") {
            val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
            json.has("aggregateId") shouldBe false
            json.has("notifikasjonId") shouldBe true
        }
    }
})