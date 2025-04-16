package no.nav.arbeidsgiver.notifikasjon

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit tests for historical and current formats that
 * may be seen in kafka log.
 */
class HendelseSerializationTest {

    @Test
    fun `Serialiserer av timestamps`() {
        val oppgaveOpprettet = HardDelete(
            virksomhetsnummer = "",
            aggregateId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            deletedAt = OffsetDateTime.parse("2020-02-02T02:02+02"),
            grupperingsid = null,
            merkelapp = "merkelapp",
        )

        // OffsetDateTime serialiseres med offset
        assertEquals(
            "2020-02-02T02:02:00+02:00",
            kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))["deletedAt"].asText()
        )

        val fristUtsatt = HendelseModel.FristUtsatt(
            virksomhetsnummer = "1",
            hendelseId = uuid("1"),
            produsentId = "produsent",
            kildeAppNavn = "app",
            notifikasjonId = uuid("2"),
            fristEndretTidspunkt = Instant.parse("2020-01-01T01:02:03Z"),
            frist = LocalDate.of(2020, 1, 20),
            påminnelse = null,
            merkelapp = "merkelapp"
        )

        // Instant serialiseres med Z
        assertEquals(
            "2020-01-01T01:02:03Z",
            kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(fristUtsatt))["fristEndretTidspunkt"].asText()
        )

        // LocalDate serialiseres som YYYY-MM-DD
        assertEquals(
            "2020-01-20",
            kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(fristUtsatt))["frist"].asText()
        )
    }

    @Test
    fun `kun 'mottaker'`() {
        val oppgaveOpprettet = OppgaveOpprettet(
            virksomhetsnummer = "",
            notifikasjonId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            merkelapp = "",
            eksternId = "",
            mottakere = listOf(
                AltinnMottaker(
                    serviceCode = "1",
                    serviceEdition = "2",
                    virksomhetsnummer = "3",
                )
            ),
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


        val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
        assertFalse(json.has("mottaker"))
        assertEquals("1", json["mottakere"][0]["serviceCode"].asText())
        assertEquals("2", json["mottakere"][0]["serviceEdition"].asText())
        assertEquals("3", json["mottakere"][0]["virksomhetsnummer"].asText())
    }

    /* Dette hadde vært fint å få den mer korrekte 'aggregateId' i json, så bare å slette
     * denne testen hvis det endres! */
    @Test
    fun `har ikke 'aggregateId', men 'notifikasjonId' i HardDelete json`() {
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

        val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
        assertFalse(json.has("aggregateId"))
        assertTrue(json.has("notifikasjonId"))
    }

    /* Dette hadde vært fint å få den mer korrekte 'aggregateId' i json, så bare å slette
     * denne testen hvis det endres! */
    @Test
    fun `har ikke 'aggregateId', men 'notifikasjonId' i SoftDelete json`() {
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

        val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
        assertFalse(json.has("aggregateId"))
        assertTrue(json.has("notifikasjonId"))
    }

    @Test
    fun `har ikke 'aggregateId' i json`() {
        val oppgaveOpprettet = OppgaveOpprettet(
            virksomhetsnummer = "",
            notifikasjonId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            merkelapp = "",
            eksternId = "",
            mottakere = listOf(
                AltinnMottaker(
                    serviceCode = "1",
                    serviceEdition = "2",
                    virksomhetsnummer = "3",
                )
            ),
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


        val json = kafkaObjectMapper.readTree(kafkaObjectMapper.writeValueAsString(oppgaveOpprettet))
        assertFalse(json.has("aggregateId"))
        assertTrue(json.has("notifikasjonId"))
    }
}