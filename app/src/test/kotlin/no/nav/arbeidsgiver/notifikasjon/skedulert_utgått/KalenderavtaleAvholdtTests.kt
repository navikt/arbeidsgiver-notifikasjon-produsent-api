package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand.*
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class KalenderavtaleAvholdtTests : DescribeSpec({
    val localDateTimeNow = LocalDateTime.now()
    val tidspunktSomHarPassert = localDateTimeNow.minusHours(1)
    val tidspunktSomIkkeHarPassert = localDateTimeNow.plusHours(1)

    val sakOpprettet = HendelseModel.SakOpprettet(
        hendelseId = uuid("1"),
        virksomhetsnummer = "1",
        produsentId = "",
        kildeAppNavn = "",
        sakId = uuid("1"),
        grupperingsid = "42",
        merkelapp = "test",
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        tittel = "test",
        tilleggsinformasjon = null,
        lenke = "#test",
        oppgittTidspunkt = OffsetDateTime.now(),
        mottattTidspunkt = OffsetDateTime.now(),
        nesteSteg = null,
        hardDelete = null,
    )
    val opprettet = HendelseModel.KalenderavtaleOpprettet(
        virksomhetsnummer = sakOpprettet.virksomhetsnummer,
        merkelapp = sakOpprettet.merkelapp,
        eksternId = "42",
        mottakere = sakOpprettet.mottakere,
        hendelseId = uuid("2"),
        notifikasjonId = uuid("2"),
        tekst = "test",
        lenke = "#test",
        opprettetTidspunkt = OffsetDateTime.now(),
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = "42",
        eksterneVarsler = listOf(),
        hardDelete = null,
        sakId = sakOpprettet.sakId,
        påminnelse = null,
        tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
        startTidspunkt = localDateTimeNow,
        sluttTidspunkt = null,
        lokasjon = null,
        erDigitalt = false,
    )
    val oppdatert = HendelseModel.KalenderavtaleOppdatert(
        virksomhetsnummer = sakOpprettet.virksomhetsnummer,
        merkelapp = sakOpprettet.merkelapp,
        hendelseId = uuid("3"),
        notifikasjonId = uuid("2"),
        tekst = "test",
        lenke = "#test",
        opprettetTidspunkt = Instant.now(),
        oppdatertTidspunkt = opprettet.opprettetTidspunkt.toInstant(),
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = "42",
        eksterneVarsler = listOf(),
        hardDelete = null,
        påminnelse = null,
        tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
        startTidspunkt = localDateTimeNow,
        sluttTidspunkt = null,
        lokasjon = null,
        erDigitalt = false,
        idempotenceKey = null,
    )

    describe("noop når starttidspunkt ikke har passert enda") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomIkkeHarPassert))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser shouldBe emptyList()
    }

    describe("noop når aggregat er fjernet") {
        val (repo, hendelseProdusent, service) = setupTestApp()
        withData(
            listOf(
                HendelseModel.HardDelete(
                    aggregateId = opprettet.aggregateId,
                    virksomhetsnummer = opprettet.virksomhetsnummer,
                    hendelseId = UUID.randomUUID(),
                    produsentId = opprettet.virksomhetsnummer,
                    kildeAppNavn = opprettet.virksomhetsnummer,
                    deletedAt = OffsetDateTime.now(),
                    grupperingsid = null,
                    merkelapp = opprettet.merkelapp,
                ),
                opprettet.copy(startTidspunkt = tidspunktSomHarPassert),
                oppdatert.copy(startTidspunkt = tidspunktSomHarPassert),
            )
        ) {

            repo.oppdaterModellEtterHendelse(it)
            service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
            hendelseProdusent.hendelser shouldBe emptyList()
        }
    }

    describe("noop når status er avholdt elelr avlyst") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(opprettet.copy(
            notifikasjonId = uuid("11"),
            startTidspunkt = tidspunktSomHarPassert,
            tilstand = AVLYST
        ))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
        hendelseProdusent.hendelser shouldBe emptyList()

        repo.oppdaterModellEtterHendelse(opprettet.copy(
            notifikasjonId = uuid("22"),
            startTidspunkt = tidspunktSomHarPassert,
            tilstand = AVHOLDT
        ))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
        hendelseProdusent.hendelser shouldBe emptyList()
    }

    describe("Skedulerer avholdt når starttidspunkt har passert") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomHarPassert))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser.first().also {
            it as HendelseModel.KalenderavtaleOppdatert
            it.tilstand shouldBe AVHOLDT
        }
    }

    describe("Skedulerer avholdt når starttidspunkt har passert og det finnes et starttidspunkt på kø som ikke har passert") {
        /**
         * denne testen passer på at vi klarer å skille på forskjellige aggregater
         */

        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(
            opprettet.copy(
                notifikasjonId = uuid("11"),
                startTidspunkt = tidspunktSomIkkeHarPassert
            )
        )
        repo.oppdaterModellEtterHendelse(
            opprettet.copy(
                notifikasjonId = uuid("22"),
                startTidspunkt = tidspunktSomHarPassert
            )
        )
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser shouldHaveSize 1
        hendelseProdusent.hendelser.first().also {
            it as HendelseModel.KalenderavtaleOppdatert
            it.tilstand shouldBe AVHOLDT
            it.aggregateId shouldBe uuid("22")
        }
    }

    describe("skedulering fjernes når kalenderavtale setter til slutt tilstand") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomHarPassert))
        repo.oppdaterModellEtterHendelse(oppdatert.copy(tilstand = AVLYST))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser shouldBe emptyList()

        repo.oppdaterModellEtterHendelse(
            opprettet.copy(
                notifikasjonId = uuid("42"),
                startTidspunkt = tidspunktSomHarPassert
            )
        )
        repo.oppdaterModellEtterHendelse(
            oppdatert.copy(
                notifikasjonId = uuid("42"),
                tilstand = AVHOLDT
            )
        )
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser shouldBe emptyList()
    }


    describe("oppdater etter avholdt fører til ny skedulert avholdt") {
        /**
         * siden tilstand kan settes via API og vi automatisk setter til avholdt
         * så bør vi passe på at oppgaver som gjenåpnes fortsatt settes til avholdt
         * tenkt scenario er at lagg i en produsent fører til at vi får en oppdatering på tilstand etter at vi har satt
         * avholdt. da bør kalenderavtalen forbli avholdt og ikke ende i en åpen state
         */

        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomHarPassert))
        repo.oppdaterModellEtterHendelse(oppdatert.copy(tilstand = AVLYST))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
        hendelseProdusent.hendelser shouldBe emptyList()

        repo.oppdaterModellEtterHendelse(oppdatert.copy(tilstand = ARBEIDSGIVER_HAR_GODTATT))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser.first().also {
            it as HendelseModel.KalenderavtaleOppdatert
            it.tilstand shouldBe AVHOLDT
        }
    }

})

private fun DescribeSpec.setupTestApp(): Triple<SkedulertUtgåttRepository, FakeHendelseProdusent, SkedulertUtgåttService> {
    val database = testDatabase(SkedulertUtgått.databaseConfig)
    val repo = SkedulertUtgåttRepository(database)
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertUtgåttService(repo, hendelseProdusent)
    return Triple(repo, hendelseProdusent, service)
}