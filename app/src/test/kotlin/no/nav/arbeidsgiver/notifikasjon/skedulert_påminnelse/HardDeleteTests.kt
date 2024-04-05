package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.*


class HardDeleteTests: DescribeSpec({
    val metadata = PartitionHendelseMetadata(0, 0)

    describe("Hvis oppgave blir hard deleted") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(hendelseProdusent)

        val oppgave = oppgaveMedPåminnelse()
        service.processHendelse(oppgave, metadata)
        service.processHendelse(
            hardDelete(
                aggregateId = oppgave.aggregateId,
                grupperingsId = null,
                merkelapp = null,
            ), metadata
        )

        it("Sendes ingen påminnelse") {
            service.sendAktuellePåminnelser()
            hendelseProdusent.hendelser should beEmpty()
        }
    }

    describe("Hvis saker blir hard deleted, og sak opprettes etter oppgaver") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(hendelseProdusent)

        oppgaveMedPåminnelse(
            merkelapp = "m1",
            grupperingsId = "g1",
        ).also { service.processHendelse(it, metadata) }

        val annenGruppe = oppgaveMedPåminnelse(
            merkelapp = "m1",
            grupperingsId = "g2"
        ).also { service.processHendelse(it, metadata) }

        val annenMerkelapp = oppgaveMedPåminnelse(
            merkelapp = "m2",
            grupperingsId = "g1"
        ).also { service.processHendelse(it, metadata) }

        val annenGruppeOgMerkelapp = oppgaveMedPåminnelse(
            merkelapp = "m2",
            grupperingsId = "g2"
        ).also { service.processHendelse(it, metadata) }

        val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
            .also { service.processHendelse(it, metadata) }

        service.processHendelse(
            hardDelete(
                aggregateId = sak1.aggregateId,
                merkelapp = "m1",
                grupperingsId = "g1"
            ), metadata
        )

        it("sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher") {
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            bestillingerUtført shouldContainExactlyInAnyOrder listOf(
                annenGruppe.aggregateId,
                annenMerkelapp.aggregateId,
                annenGruppeOgMerkelapp.aggregateId,
            )
        }
    }

    describe("Hvis saker blir hard deleted, og sak opprettes før oppgaver") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(hendelseProdusent)

        val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
            .also { service.processHendelse(it, metadata) }

        oppgaveMedPåminnelse(
            merkelapp = "m1",
            grupperingsId = "g1",
        ).also { service.processHendelse(it, metadata) }

        val annenGruppe = oppgaveMedPåminnelse(
            merkelapp = "m1",
            grupperingsId = "g2"
        ).also { service.processHendelse(it, metadata) }

        val annenMerkelapp = oppgaveMedPåminnelse(
            merkelapp = "m2",
            grupperingsId = "g1"
        ).also { service.processHendelse(it, metadata) }

        val annenGruppeOgMerkelapp = oppgaveMedPåminnelse(
            merkelapp = "m2",
            grupperingsId = "g2"
        ).also { service.processHendelse(it, metadata) }

        service.processHendelse(
            hardDelete(
                aggregateId = sak1.aggregateId,
                merkelapp = "m1",
                grupperingsId = "g1"
            ), metadata
        )

        it("sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher") {
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            bestillingerUtført shouldContainExactlyInAnyOrder listOf(
                annenGruppe.aggregateId,
                annenMerkelapp.aggregateId,
                annenGruppeOgMerkelapp.aggregateId,
            )
        }
    }

    describe("Hvis saker blir hard deleted, og oppgaver opprettes etter hard delete") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(hendelseProdusent)

        val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
            .also { service.processHendelse(it, metadata) }

        service.processHendelse(
            hardDelete(
                aggregateId = sak1.aggregateId,
                merkelapp = "m1",
                grupperingsId = "g1"
            ), metadata
        )

        oppgaveMedPåminnelse(
            merkelapp = "m1",
            grupperingsId = "g1",
        ).also { service.processHendelse(it, metadata) }

        val annenGruppe = oppgaveMedPåminnelse(
            merkelapp = "m1",
            grupperingsId = "g2"
        ).also { service.processHendelse(it, metadata) }

        val annenMerkelapp = oppgaveMedPåminnelse(
            merkelapp = "m2",
            grupperingsId = "g1"
        ).also { service.processHendelse(it, metadata) }

        val annenGruppeOgMerkelapp = oppgaveMedPåminnelse(
            merkelapp = "m2",
            grupperingsId = "g2"
        ).also { service.processHendelse(it, metadata) }

        it("sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher") {
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            bestillingerUtført shouldContainExactlyInAnyOrder listOf(
                annenGruppe.aggregateId,
                annenMerkelapp.aggregateId,
                annenGruppeOgMerkelapp.aggregateId,
            )
        }
    }
})

private fun oppgaveMedPåminnelse(
    merkelapp: String = "m1",
    grupperingsId: String? = null,
): HendelseModel.OppgaveOpprettet {
    val notifikasjonId = UUID.randomUUID()
    val opprettetTidspunkt = OffsetDateTime.now().minusDays(14)
    val tidspunkt = LocalDate.now().minusDays(1).atTime(LocalTime.MAX)
    return HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        eksternId = notifikasjonId.toString(),
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        hendelseId = notifikasjonId,
        notifikasjonId = notifikasjonId,
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = opprettetTidspunkt,
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = grupperingsId,
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = HendelseModel.Påminnelse(
            tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
                tidspunkt,
                opprettetTidspunkt,
                null,
                null
            ),
            eksterneVarsler = listOf()
        ),
        sakId = null,
    )
}

private fun opprettSak(
    merkelapp: String,
    grupperingsId: String,
): HendelseModel.SakOpprettet {
    val sakId = UUID.randomUUID()
    return HendelseModel.SakOpprettet(
        sakId = sakId,
        hendelseId = sakId,
        virksomhetsnummer = "1",
        produsentId = "",
        kildeAppNavn = "",
        grupperingsid = grupperingsId,
        merkelapp = merkelapp,
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        tittel = "",
        lenke = "",
        oppgittTidspunkt = null,
        mottattTidspunkt = null,
        hardDelete = null,
    )
}

private fun hardDelete(
    aggregateId: UUID,
    merkelapp: String?,
    grupperingsId: String?,
) = HendelseModel.HardDelete(
    virksomhetsnummer = "1",
    aggregateId = aggregateId,
    hendelseId = UUID.randomUUID(),
    produsentId = "",
    kildeAppNavn = "",
    deletedAt = OffsetDateTime.now(),
    grupperingsid = grupperingsId,
    merkelapp = merkelapp,
)
