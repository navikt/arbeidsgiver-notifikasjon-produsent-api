package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.kafkaObjectMapper
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import org.junit.jupiter.api.Assertions.assertNull
import java.time.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for historical and current formats that
 * may be seen in kafka log.
 */
class HendelseDeserializationTest {

    @Test
    fun `sak opprettet før nye felter er lagt til kan parses`() {
        val sakOpprettet = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
              "@type": "SakOpprettet",
              "hendelseId": "da89eafe-b31b-11eb-8529-000000000017",
              "virksomhetsnummer": "1",
              "produsentId": "1",
              "kildeAppNavn": "1",
              "sakId": "da89eafe-b31b-11eb-8529-000000000017",
              "grupperingsid": "da89eafe-b31b-11eb-8529-000000000017",
              "merkelapp": "tag",
              "mottakere": [
                {
                  "@type": "altinn",
                  "serviceCode": "1",
                  "serviceEdition": "1",
                  "virksomhetsnummer": "1"
                },
                {
                  "@type": "naermesteLeder",
                  "naermesteLederFnr": "2",
                  "ansattFnr": "1",
                  "virksomhetsnummer": "1"
                }
              ],
              "tittel": "foo",
              "lenke": "#foo",
              "oppgittTidspunkt": "2021-01-01T13:37:00Z",
              "mottattTidspunkt": "2024-09-27T11:15:11.077955+02:00",
              "hardDelete": null
            }
        """.trimIndent()
        )

        sakOpprettet as SakOpprettet
        assertNull(sakOpprettet.nesteSteg)
        assertNull(sakOpprettet.tilleggsinformasjon)
    }

    @Test
    fun `kun 'mottaker'`() {
        val oppgaveOpprettet = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
                "@type": "OppgaveOpprettet",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("0")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "merkelapp": "",
                "eksternId": "",
                "mottaker": {
                    "@type": "altinn",
                    "serviceCode": "1",
                    "serviceEdition": "2",
                    "virksomhetsnummer": "3"
                },
                "tekst": "",
                "lenke": "",
                "opprettetTidspunkt": "2020-01-01T01:01+01"
            }
        """
        )

        oppgaveOpprettet as OppgaveOpprettet
        val mottaker = oppgaveOpprettet.mottakere.single() as AltinnMottaker
        assertEquals("1", mottaker.serviceCode)
        assertEquals("2", mottaker.serviceEdition)
        assertEquals("3", mottaker.virksomhetsnummer)
    }

    @Test
    fun `ikke 'mottaker', kun 'mottakere'`() {
        val oppgaveOpprettet = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
                "@type": "OppgaveOpprettet",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("0")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "merkelapp": "",
                "eksternId": "",
                "mottakere": [
                    {
                        "@type": "altinn",
                        "serviceCode": "1",
                        "serviceEdition": "2",
                        "virksomhetsnummer": "3"
                    }
                ],
                "tekst": "",
                "lenke": "",
                "opprettetTidspunkt": "2020-01-01T01:01+01"
            }
        """
        )

        oppgaveOpprettet as OppgaveOpprettet
        val mottaker = oppgaveOpprettet.mottakere.single() as AltinnMottaker
        assertEquals("1", mottaker.serviceCode)
        assertEquals("2", mottaker.serviceEdition)
        assertEquals("3", mottaker.virksomhetsnummer)
    }

    @Test
    fun `Støtter hard delete uten grupperingsid`() {
        val hardDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
                "@type": "HardDelete",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("1")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "deletedAt": "2020-01-01T01:01+01"
            }
        """
        )

        hardDelete as HardDelete
        assertNull(hardDelete.grupperingsid)
    }

    @Test
    fun `Støtter soft delete uten grupperingsid`() {
        val softDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
                "@type": "SoftDelete",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("1")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "deletedAt": "2020-01-01T01:01+01"
            }
        """
        )

        softDelete as SoftDelete
        assertNull(softDelete.grupperingsid)
    }

    @Test
    fun `Støtter soft delete med ikke-uuid grupperingsid`() {
        val softDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
                "@type": "SoftDelete",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("1")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "deletedAt": "2020-01-01T01:01+01",
                "grupperingsid": "1234xx",
                "merkelapp": "merkeliglapp"
            }
        """
        )

        softDelete as SoftDelete
        assertEquals("1234xx", softDelete.grupperingsid)
        assertEquals("merkeliglapp", softDelete.merkelapp)
    }

    @Test
    fun `Støtter hard delete med ikke-uuid grupperingsid`() {
        val hardDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
                "@type": "HardDelete",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("1")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "deletedAt": "2020-01-01T01:01+01",
                "grupperingsid": "1234xx",
                "merkelapp": "merkeliglapp"
            }
        """
        )

        hardDelete as HardDelete
        assertEquals("1234xx", hardDelete.grupperingsid)
        assertEquals("merkeliglapp", hardDelete.merkelapp)
    }

    @Test
    fun `leser 'notifikasjon', selv om den heter 'aggregateId' i kotlin`() {
        val hardDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
            {
                "@type": "HardDelete",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("1")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "deletedAt": "2020-01-01T01:01+01"
            }
        """
        )

        hardDelete as HardDelete
        assertEquals(uuid("0"), hardDelete.hendelseId)
        assertEquals(uuid("1"), hardDelete.aggregateId)
    }

    @Test
    fun `Kan deserialisere timestamp med offsets til OffsetDateTime`() {
        val hardDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
                    { 
                    "@type": "HardDelete",
                    "virksomhetsnummer": "123456789",
                    "notifikasjonId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "hendelseId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "produsentId": "en-produsent",
                    "kildeAppNavn": "en:app:foo",
                    "deletedAt": "2020-02-02T01:02:03+04:00"
                    }
                """
        )

        hardDelete as HardDelete
        assertEquals(
            OffsetDateTime.of(
                LocalDate.parse("2020-02-02"),
                LocalTime.parse("01:02:03"),
                ZoneOffset.ofHours(4),
            ),
            hardDelete.deletedAt
        )
    }

    @Test
    fun `Kan deserialisere timestamp med Z til OffsetDateTime`() {
        val hardDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
                    { 
                    "@type": "HardDelete",
                    "virksomhetsnummer": "123456789",
                    "notifikasjonId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "hendelseId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "produsentId": "en-produsent",
                    "kildeAppNavn": "en:app:foo",
                    "deletedAt": "2020-02-02T01:02:03Z"
                    }
                """
        )

        hardDelete as HardDelete
        assertEquals(
            OffsetDateTime.of(
                LocalDate.parse("2020-02-02"),
                LocalTime.parse("01:02:03"),
                ZoneOffset.ofHours(0),
            ),
            hardDelete.deletedAt
        )
    }

    @Test
    fun `Kan deserialisere timestamp som epoch-offset til OffsetDateTime`() {
        /* Mener å huske at vi i starten ikke hadde konfigurert jackson til å skrive ut på ISO-format.*/
        val hardDelete = kafkaObjectMapper.readValue<Hendelse>(
            """
                    { 
                    "@type": "HardDelete",
                    "virksomhetsnummer": "123456789",
                    "notifikasjonId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "hendelseId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "produsentId": "en-produsent",
                    "kildeAppNavn": "en:app:foo",
                    "deletedAt": 1580590923
                    }
                """
        )

        hardDelete as HardDelete
        assertEquals(
            OffsetDateTime.of(
                LocalDate.parse("2020-02-01"),
                LocalTime.parse("21:02:03"),
                ZoneOffset.ofHours(0),
            ), hardDelete.deletedAt
        )
    }

    @Test
    fun `Kan deserialisere timestamp med Z til Instant`() {
        val fristUtsatt = kafkaObjectMapper.readValue<Hendelse>(
            """
                    { 
                    "@type": "FristUtsatt",
                    "virksomhetsnummer": "123456789",
                    "notifikasjonId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "hendelseId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "produsentId": "en-produsent",
                    "kildeAppNavn": "en:app:foo",
                    "fristEndretTidspunkt": "2020-02-02T01:02:03Z",
                    "frist": "2020-01-01",
                    "påminnelse": null,
                    "merkelapp": "merkelapp"
                    }
                """
        )
        fristUtsatt as HendelseModel.FristUtsatt
        assertEquals(
            Instant.parse("2020-02-02T01:02:03Z"),
            fristUtsatt.fristEndretTidspunkt
        )
    }

    @Test
    fun `Kan deserialisere timestamp med offset til justert Instant`() {
        val fristUtsatt = kafkaObjectMapper.readValue<Hendelse>(
            """
                    { 
                    "@type": "FristUtsatt",
                    "virksomhetsnummer": "123456789",
                    "notifikasjonId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "hendelseId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "produsentId": "en-produsent",
                    "kildeAppNavn": "en:app:foo",
                    "fristEndretTidspunkt": "2020-02-02T01:02:03+05:00",
                    "frist": "2020-01-01",
                    "påminnelse": null,
                    "merkelapp": "merkelapp"
                    }
                """
        )
        fristUtsatt as HendelseModel.FristUtsatt
        assertEquals(
            Instant.parse("2020-02-01T20:02:03Z"),
            fristUtsatt.fristEndretTidspunkt
        )
    }

    @Test
    fun `Kan deserialisere timestamp som epoch-offset til Instant`() {
        /* Mener å huske at vi i starten ikke hadde konfigurert jackson til å skrive ut på ISO-format.*/
        val fristUtsatt = kafkaObjectMapper.readValue<Hendelse>(
            """
                    { 
                    "@type": "FristUtsatt",
                    "virksomhetsnummer": "123456789",
                    "notifikasjonId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "hendelseId": "58c07c45-c9ce-4f16-abbb-8d7f1d920cad",
                    "produsentId": "en-produsent",
                    "kildeAppNavn": "en:app:foo",
                    "fristEndretTidspunkt": 1580590923,
                    "frist": "2020-01-01",
                    "påminnelse": null,
                    "merkelapp": "merkelapp"
                    }
                """
        )
        fristUtsatt as HendelseModel.FristUtsatt
        assertEquals(
            Instant.parse("2020-02-01T21:02:03Z"),
            fristUtsatt.fristEndretTidspunkt
        )
    }
}