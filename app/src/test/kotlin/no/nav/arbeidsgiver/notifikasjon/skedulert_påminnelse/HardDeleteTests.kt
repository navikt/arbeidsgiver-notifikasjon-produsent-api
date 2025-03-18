package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.*


class HardDeleteTests : DescribeSpec({
    fun deletePåminnelse(
        deleteType: String,
        deleteHendelse: (
            aggregateId: UUID,
            grupperingsId: String?,
            merkelapp: String?
        ) -> HendelseModel.Hendelse
    ) {
        describe("Hvis oppgave blir $deleteType deleted") {
            val (service, hendelseProdusent) = setupEngine()

            val oppgave = oppgaveMedPåminnelse()
            service.processHendelse(oppgave)
            service.processHendelse(
                deleteHendelse(
                    oppgave.aggregateId,
                    null,
                    null,
                )
            )

            it("Sendes ingen påminnelse") {
                service.sendAktuellePåminnelser()
                hendelseProdusent.hendelser should beEmpty()
            }
        }

        describe("Hvis saker blir $deleteType deleted, og sak opprettes etter oppgaver") {
            val (service, hendelseProdusent) = setupEngine()

            oppgaveMedPåminnelse(
                merkelapp = "m1",
                grupperingsId = "g1",
            ).also { service.processHendelse(it) }

            val annenGruppe = oppgaveMedPåminnelse(
                merkelapp = "m1",
                grupperingsId = "g2"
            ).also { service.processHendelse(it) }

            val annenMerkelapp = oppgaveMedPåminnelse(
                merkelapp = "m2",
                grupperingsId = "g1"
            ).also { service.processHendelse(it) }

            val annenGruppeOgMerkelapp = oppgaveMedPåminnelse(
                merkelapp = "m2",
                grupperingsId = "g2"
            ).also { service.processHendelse(it) }

            val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
                .also { service.processHendelse(it) }

            service.processHendelse(
                deleteHendelse(
                    sak1.aggregateId,
                    "g1",
                    "m1"
                )
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

        describe("Hvis saker blir $deleteType deleted, og sak opprettes før oppgaver") {
            val (service, hendelseProdusent) = setupEngine()

            val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
                .also { service.processHendelse(it) }

            oppgaveMedPåminnelse(
                merkelapp = "m1",
                grupperingsId = "g1",
            ).also { service.processHendelse(it) }

            val annenGruppe = oppgaveMedPåminnelse(
                merkelapp = "m1",
                grupperingsId = "g2"
            ).also { service.processHendelse(it) }

            val annenMerkelapp = oppgaveMedPåminnelse(
                merkelapp = "m2",
                grupperingsId = "g1"
            ).also { service.processHendelse(it) }

            val annenGruppeOgMerkelapp = oppgaveMedPåminnelse(
                merkelapp = "m2",
                grupperingsId = "g2"
            ).also { service.processHendelse(it) }

            service.processHendelse(
                deleteHendelse(
                    sak1.aggregateId,
                    "g1",
                    "m1"
                )
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

        describe("Hvis saker blir $deleteType deleted, og oppgaver opprettes etter $deleteType delete") {
            val (service, hendelseProdusent) = setupEngine()

            val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
                .also { service.processHendelse(it) }

            service.processHendelse(
                deleteHendelse(
                    sak1.aggregateId,
                    "g1",
                    "m1"
                )
            )

            oppgaveMedPåminnelse(
                merkelapp = "m1",
                grupperingsId = "g1",
            ).also { service.processHendelse(it) }

            val annenGruppe = oppgaveMedPåminnelse(
                merkelapp = "m1",
                grupperingsId = "g2"
            ).also { service.processHendelse(it) }

            val annenMerkelapp = oppgaveMedPåminnelse(
                merkelapp = "m2",
                grupperingsId = "g1"
            ).also { service.processHendelse(it) }

            val annenGruppeOgMerkelapp = oppgaveMedPåminnelse(
                merkelapp = "m2",
                grupperingsId = "g2"
            ).also { service.processHendelse(it) }

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
    }

    deletePåminnelse("hard", ::hardDelete)
    deletePåminnelse("soft", ::softDelete)
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
        nesteSteg = null,
        hardDelete = null,
        tilleggsinformasjon = null
    )
}

private fun hardDelete(
    aggregateId: UUID,
    grupperingsId: String?,
    merkelapp: String?,
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

private fun softDelete(
    aggregateId: UUID,
    grupperingsId: String?,
    merkelapp: String?,
) = HendelseModel.SoftDelete(
    virksomhetsnummer = "1",
    aggregateId = aggregateId,
    hendelseId = UUID.randomUUID(),
    produsentId = "",
    kildeAppNavn = "",
    deletedAt = OffsetDateTime.now(),
    grupperingsid = grupperingsId,
    merkelapp = merkelapp,
)

fun DescribeSpec.setupEngine(): Pair<SkedulertPåminnelseService, FakeHendelseProdusent> {
    val hendelseProdusent = FakeHendelseProdusent()
    val database = testDatabase(SkedulertPåminnelse.databaseConfig)
    val service = SkedulertPåminnelseService(hendelseProdusent = hendelseProdusent, database = database)
    return Pair(service, hendelseProdusent)
}