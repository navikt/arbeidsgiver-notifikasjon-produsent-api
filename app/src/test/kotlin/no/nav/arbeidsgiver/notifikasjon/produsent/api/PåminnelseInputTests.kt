package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

class PåminnelseInputTests : DescribeSpec({
    context("Konkret påminnelse") {
        it("Påminnelsestidspunkt er blir satt riktig") {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val konkretPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = LocalDateTime.parse("2024-01-07T01:01"), etterOpprettelse = null, foerFrist = null, foerStartTidspunkt = null),
                eksterneVarsler = emptyList());

            val result = konkretPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123");

            val expectedPåminnelseTidspunkt = konkretPåminnelseInput.tidspunkt.konkret!!.inOsloAsInstant()
            result.tidspunkt.påminnelseTidspunkt shouldBe expectedPåminnelseTidspunkt
        }

        it("Påminnelse er etter oppgavens frist"){
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val konkretPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = LocalDateTime.parse("2024-01-07T01:01"), etterOpprettelse = null, foerFrist = null, foerStartTidspunkt = null),
                eksterneVarsler = emptyList());

            assertThrows<UgyldigPåminnelseTidspunktException> {
                konkretPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, LocalDate.parse("2024-01-06"), null, "123");
            }
        }

        it("Påminnelse er før notifikasjon er opprettet") {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-02-01T01:01Z")
            val konkretPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = LocalDateTime.parse("2024-01-07T01:01"), etterOpprettelse = null, foerFrist = null, foerStartTidspunkt = null),
                eksterneVarsler = emptyList());

            assertThrows<UgyldigPåminnelseTidspunktException> {
                konkretPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123");
            }
        }
    }
    context("PåminnelsesTidspunkt relativ til OppgaveOpprettet") {
        it("Påminnelsetidspunkt blir satt riktig") {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val etterOpprettelsePåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = null, etterOpprettelse = ISO8601Period.parse("P7DT"), foerFrist = null, foerStartTidspunkt = null),
                eksterneVarsler = emptyList());

            val result = etterOpprettelsePåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123");

            val expectedPåminnelseTidspunkt = notifikasjonOpprettetTidspunkt.plusDays(7).toInstant()
            result.tidspunkt.påminnelseTidspunkt shouldBe expectedPåminnelseTidspunkt
        }
    }

    context("PåminnelsesTidspunkt relativ til frist på oppgave") {
        it("Påminnelsetidspunkt blir satt riktig") {
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val oppgaveFrist = LocalDate.parse("2024-02-08")
            val førFristPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = null, etterOpprettelse = null, foerFrist = ISO8601Period.parse("P7DT"), foerStartTidspunkt = null),
                eksterneVarsler = emptyList());

            val result = førFristPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, oppgaveFrist, null, "123");

            val expectedPåminnelseTidspunkt = LocalDateTime.of(oppgaveFrist.minusDays(7), LocalTime.MAX).inOsloAsInstant()
            result.tidspunkt.påminnelseTidspunkt shouldBe expectedPåminnelseTidspunkt
        }

        it ("Frist på oppgaven er null"){
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-02-01T01:01Z")
            val førFristPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = null, etterOpprettelse = null, foerFrist = ISO8601Period.parse("P7DT"), foerStartTidspunkt = null),
                eksterneVarsler = emptyList());

            assertThrows<UgyldigPåminnelseTidspunktException> {
                førFristPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, null, null, "123");
            }
        }

        it ("Påminnelse vil være før oppgaven er opprettet"){
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-02-01T01:01Z")
            val førFristPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = null, etterOpprettelse = null, foerFrist = ISO8601Period.parse("P7DT"), foerStartTidspunkt = null),
                eksterneVarsler = emptyList());

            assertThrows<UgyldigPåminnelseTidspunktException> {
                førFristPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, LocalDate.parse("2024-02-02"), null, "123");
            }
        }
    }
    context("PåminnelsesTidspunkt er relativ til startTidspunkt på kalenderavtale"){
        it("Påminnelsetidspunkt blir satt riktig"){
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val kalenderAvtaleStartTidspunkt = LocalDateTime.parse("2024-01-15T01:01")
            val førStartTidspunktPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = null, etterOpprettelse = null, foerFrist = null, foerStartTidspunkt = ISO8601Period.parse("P7DT")),
                eksterneVarsler = emptyList());

            val result = førStartTidspunktPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, null, kalenderAvtaleStartTidspunkt, "123");
            val expectedPåminnelseTidspunkt = kalenderAvtaleStartTidspunkt.minusDays(7).inOsloAsInstant()
            result.tidspunkt.påminnelseTidspunkt shouldBe expectedPåminnelseTidspunkt
        }

        it("Påminnelsetidspunkt vil være før kalenderavtalen er opprettet"){
            val notifikasjonOpprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01Z")
            val kalenderAvtaleStartTidspunkt = LocalDateTime.parse("2024-01-03T01:01")
            val førStartTidspunktPåminnelseInput = PaaminnelseInput(
                tidspunkt = PaaminnelseTidspunktInput(konkret = null, etterOpprettelse = null, foerFrist = null, foerStartTidspunkt = ISO8601Period.parse("P7DT")),
                eksterneVarsler = emptyList());

            assertThrows<UgyldigPåminnelseTidspunktException> {
                førStartTidspunktPåminnelseInput.tilDomene(notifikasjonOpprettetTidspunkt, null, kalenderAvtaleStartTidspunkt, "123");
            }
        }
    }
})
