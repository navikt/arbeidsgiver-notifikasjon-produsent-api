package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class HardDeleteTest {

    @Test
    fun `Hvis oppgave blir hard deleted`() = withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(
            hendelseProdusent = hendelseProdusent,
            database = database
        )

        val oppgave = oppgaveMedPåminnelse()
        service.processHendelse(oppgave)
        service.processHendelse(
            hardDelete(
                oppgave.aggregateId,
                null,
                null
            )
        )

        // Sendes ingen påminnelse
        service.sendAktuellePåminnelser()
        assertTrue(hendelseProdusent.hendelser.isEmpty())
    }

    @Test
    fun `Hvis saker blir hard deleted, og sak opprettes etter oppgaver`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
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

            val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
                .also { service.processHendelse(it) }

            service.processHendelse(
                hardDelete(
                    sak1.aggregateId,
                    "g1",
                    "m1"
                )
            )

            // sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            assertEquals(
                listOf(
                    annenGruppe.aggregateId,
                    annenMerkelapp.aggregateId,
                    annenGruppeOgMerkelapp.aggregateId,
                ), bestillingerUtført
            )
        }

    @Test
    fun `Hvis saker blir hard deleted, og sak opprettes før oppgaver`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )

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
                hardDelete(
                    sak1.aggregateId,
                    "g1",
                    "m1"
                )
            )

            // sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            assertEquals(
                listOf(
                    annenGruppe.aggregateId,
                    annenMerkelapp.aggregateId,
                    annenGruppeOgMerkelapp.aggregateId,
                ), bestillingerUtført
            )
        }

    @Test
    fun `Hvis saker blir hard deleted, og oppgaver opprettes etter hard delete`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )

            val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
                .also { service.processHendelse(it) }

            service.processHendelse(
                hardDelete(
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

            // sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            assertEquals(
                listOf(
                    annenGruppe.aggregateId,
                    annenMerkelapp.aggregateId,
                    annenGruppeOgMerkelapp.aggregateId,
                ), bestillingerUtført
            )
        }

    @Test
    fun `Hvis oppgave blir soft deleted`() = withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(
            hendelseProdusent = hendelseProdusent,
            database = database
        )

        val oppgave = oppgaveMedPåminnelse()
        service.processHendelse(oppgave)
        service.processHendelse(
            softDelete(
                oppgave.aggregateId,
                null,
                null
            )
        )

        // Sendes ingen påminnelse
        service.sendAktuellePåminnelser()
        assertTrue(hendelseProdusent.hendelser.isEmpty())
    }

    @Test
    fun `Hvis saker blir soft deleted, og sak opprettes etter oppgaver`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
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

            val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
                .also { service.processHendelse(it) }

            service.processHendelse(
                softDelete(
                    sak1.aggregateId,
                    "g1",
                    "m1"
                )
            )

            // sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            assertEquals(
                listOf(
                    annenGruppe.aggregateId,
                    annenMerkelapp.aggregateId,
                    annenGruppeOgMerkelapp.aggregateId,
                ), bestillingerUtført
            )
        }

    @Test
    fun `Hvis saker blir soft deleted, og sak opprettes før oppgaver`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )

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
                softDelete(
                    sak1.aggregateId,
                    "g1",
                    "m1"
                )
            )

            // sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            assertEquals(
                listOf(
                    annenGruppe.aggregateId,
                    annenMerkelapp.aggregateId,
                    annenGruppeOgMerkelapp.aggregateId,
                ), bestillingerUtført
            )
        }

    @Test
    fun `Hvis saker blir soft deleted, og oppgaver opprettes etter soft delete`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )

            val sak1 = opprettSak(merkelapp = "m1", grupperingsId = "g1")
                .also { service.processHendelse(it) }

            service.processHendelse(
                softDelete(
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

            // sendes ikke påminnelse hvis både merkelapp og grupperingsid matcher
            service.sendAktuellePåminnelser()
            val bestillingerUtført = hendelseProdusent.hendelser.map {
                if (it is HendelseModel.PåminnelseOpprettet)
                    it.bestillingHendelseId
                else
                    null
            }
            assertEquals(
                listOf(
                    annenGruppe.aggregateId,
                    annenMerkelapp.aggregateId,
                    annenGruppeOgMerkelapp.aggregateId,
                ), bestillingerUtført
            )
        }
}

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

@Suppress("SameParameterValue")
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

