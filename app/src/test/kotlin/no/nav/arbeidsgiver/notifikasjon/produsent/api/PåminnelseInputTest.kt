package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PåminnelseInputTest {
    @Test
    fun `Konkret påminnelse`() {
        // Påminnelsestidspunkt er blir satt riktig
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = LocalDateTime.parse("2024-01-07T01:01"),
                    etterOpprettelse = null,
                    foerFrist = null,
                    foerStartTidspunkt = null
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")

            val result = tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123")
            val expectedPåminnelseTidspunkt = tidspunkt.konkret!!.inOsloAsInstant()

            assertEquals(expectedPåminnelseTidspunkt, result.tidspunkt.påminnelseTidspunkt)
        }

        // Påminnelse er etter oppgavens frist
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = LocalDateTime.parse("2024-01-07T01:01"),
                    etterOpprettelse = null,
                    foerFrist = null,
                    foerStartTidspunkt = null
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")

            assertThrows<UgyldigPåminnelseTidspunktException> {
                tilDomene(
                    notifikasjonOpprettetTidspunkt,
                    LocalDate.parse("2024-01-06"),
                    null,
                    "123"
                )
            }
        }

        // Påminnelse er før notifikasjon er opprettet
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = LocalDateTime.parse("2024-01-07T01:01"),
                    etterOpprettelse = null,
                    foerFrist = null,
                    foerStartTidspunkt = null
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-02-01T01:01Z")

            assertThrows<UgyldigPåminnelseTidspunktException> {
                tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123")
            }
        }
    }

    @Test
    fun `PåminnelsesTidspunkt relativ til OppgaveOpprettet`() {
        // Påminnelsetidspunkt blir satt riktig
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = null,
                    etterOpprettelse = ISO8601Period.parse("P7DT"),
                    foerFrist = null,
                    foerStartTidspunkt = null
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")

            val result = tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123")
            val expectedPåminnelseTidspunkt = notifikasjonOpprettetTidspunkt.plusDays(7).toInstant()

            assertEquals(expectedPåminnelseTidspunkt, result.tidspunkt.påminnelseTidspunkt)
        }
    }

    @Test
    fun `PåminnelsesTidspunkt relativ til frist på oppgave`() {
        // Påminnelsetidspunkt blir satt riktig
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = null,
                    etterOpprettelse = null,
                    foerFrist = ISO8601Period.parse("P7DT"),
                    foerStartTidspunkt = null
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val oppgaveFrist = LocalDate.parse("2024-02-08")
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")

            val result = tilDomene(notifikasjonOpprettetTidspunkt, oppgaveFrist, null, "123")
            val expectedPåminnelseTidspunkt =
                LocalDateTime.of(oppgaveFrist.minusDays(7), LocalTime.MAX).inOsloAsInstant()

            assertEquals(expectedPåminnelseTidspunkt, result.tidspunkt.påminnelseTidspunkt)
        }


        // Frist på oppgaven er null
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = null,
                    etterOpprettelse = null,
                    foerFrist = ISO8601Period.parse("P7DT"),
                    foerStartTidspunkt = null
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-02-01T01:01Z")

            assertThrows<UgyldigPåminnelseTidspunktException> {
                tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123")
            }
        }

        // Påminnelse vil være før oppgaven er opprettet
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = null,
                    etterOpprettelse = null,
                    foerFrist = ISO8601Period.parse("P7DT"),
                    foerStartTidspunkt = null
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-02-01T01:01Z")

            assertThrows<UgyldigPåminnelseTidspunktException> {
                tilDomene(
                    notifikasjonOpprettetTidspunkt,
                    LocalDate.parse("2024-02-02"),
                    null,
                    "123"
                )
            }
        }
    }

    @Test
    fun `PåminnelsesTidspunkt er relativ til startTidspunkt på kalenderavtale`() {
        // Påminnelsetidspunkt blir satt riktig
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = null,
                    etterOpprettelse = null,
                    foerFrist = null,
                    foerStartTidspunkt = ISO8601Period.parse("P7DT")
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val kalenderAvtaleStartTidspunkt = LocalDateTime.parse("2024-01-15T01:01")

            val result = tilDomene(
                notifikasjonOpprettetTidspunkt,
                null,
                kalenderAvtaleStartTidspunkt,
                "123"
            )
            val expectedPåminnelseTidspunkt = kalenderAvtaleStartTidspunkt.minusDays(7).inOsloAsInstant()

            assertEquals(expectedPåminnelseTidspunkt, result.tidspunkt.påminnelseTidspunkt)
        }

        // Påminnelsetidspunkt vil være før kalenderavtalen er opprettet
        with(
            PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(
                    konkret = null,
                    etterOpprettelse = null,
                    foerFrist = null,
                    foerStartTidspunkt = ISO8601Period.parse("P7DT")
                ),
                eksterneVarsler = emptyList()
            )
        ) {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val kalenderAvtaleStartTidspunkt = LocalDateTime.parse("2024-01-03T01:01")

            assertThrows<UgyldigPåminnelseTidspunktException> {
                tilDomene(
                    notifikasjonOpprettetTidspunkt,
                    null,
                    kalenderAvtaleStartTidspunkt,
                    "123"
                )
            }
        }
    }
}
