package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SkedulertPåminnelseServiceTest {
    private val oppgaveOpprettet = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        merkelapp = "123",
        eksternId = "42",
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        hendelseId = uuid("1"),
        notifikasjonId = uuid("1"),
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = OffsetDateTime.now().minusDays(14),
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = null,
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )
    private val tidspunktSomHarPassert = LocalDate.now().minusDays(1).atTime(LocalTime.MAX)
    private val tidspunktSomIkkeHarPassert = LocalDate.now().plusDays(2).atTime(LocalTime.MAX)

    @Test
    fun `Skedulerer påminnelse når påminnelsestidspunkt har passert`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )
            service.processHendelse(oppgaveOpprettet.medPåminnelse(tidspunktSomHarPassert))
            service.sendAktuellePåminnelser()

            hendelseProdusent.hendelser.first() as HendelseModel.PåminnelseOpprettet
        }

    @Test
    fun `Skedulerer utgått når påminnelsestidspunkt har passert og det finnes en på kø som ikke har passert`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )
            service.processHendelse(oppgaveOpprettet.medPåminnelse(tidspunktSomIkkeHarPassert, uuid("11")))
            service.processHendelse(oppgaveOpprettet.medPåminnelse(tidspunktSomHarPassert, uuid("22")))
            service.sendAktuellePåminnelser()

            assertEquals(1, hendelseProdusent.hendelser.size)
            hendelseProdusent.hendelser.first() as HendelseModel.PåminnelseOpprettet
            assertEquals(uuid("22"), hendelseProdusent.hendelser.first().aggregateId)
        }

    @Test
    fun `noop når påminnelsestidspunkt ikke har passert enda`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )
            service.processHendelse(
                oppgaveOpprettet.medPåminnelse(tidspunktSomIkkeHarPassert)
            )
            service.sendAktuellePåminnelser()

            assertEquals(emptyList(), hendelseProdusent.hendelser)
        }

    @Test
    fun `noop når aggregat er fjernet`() = withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(
            hendelseProdusent = hendelseProdusent,
            database = database
        )
        listOf(
            HendelseModel.HardDelete(
                aggregateId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                deletedAt = OffsetDateTime.now(),
                grupperingsid = null,
                merkelapp = null,
            ),
            HendelseModel.OppgaveUtført(
                notifikasjonId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                hardDelete = null,
                nyLenke = null,
                utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
            ),
            HendelseModel.OppgaveUtgått(
                notifikasjonId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                hardDelete = null,
                utgaattTidspunkt = OffsetDateTime.now(),
                nyLenke = null,
            )
        ).forEach { hendelse ->
            hendelseProdusent.clear()
            service.processHendelse(
                oppgaveOpprettet.medPåminnelse(tidspunktSomHarPassert)
            )
            service.processHendelse(hendelse)
            service.sendAktuellePåminnelser()

            assertEquals(emptyList(), hendelseProdusent.hendelser)
        }
    }

    @Test
    fun `Skedulerer utgått for alle som har passert innenfor samme time, men ikke de som ikke har passert`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )
            val now = oppgaveOpprettet.opprettetTidspunkt.plusDays(14).toLocalDateTime()
            val passert1 = now.minus(1, ChronoUnit.HOURS) // key: 11:00
            val passert2 = now.minus(10, ChronoUnit.MINUTES) // key: 12:00
            val passert3 = now.minus(1, ChronoUnit.MINUTES) // key: 12:00
            val ikkePassert1 = now.plus(10, ChronoUnit.MINUTES) // key: 12:00

            service.processHendelse(oppgaveOpprettet.medPåminnelse(passert1, uuid("1")))
            service.processHendelse(oppgaveOpprettet.medPåminnelse(passert2, uuid("2")))
            service.processHendelse(oppgaveOpprettet.medPåminnelse(passert3, uuid("3")))
            service.processHendelse(oppgaveOpprettet.medPåminnelse(ikkePassert1, uuid("4")))

            hendelseProdusent.clear()
            service.sendAktuellePåminnelser(now.inOsloAsInstant())
            assertEquals(3, hendelseProdusent.hendelser.size)
            assertEquals(listOf(uuid("1"), uuid("2"), uuid("3")), hendelseProdusent.hendelser.map { it.aggregateId })

            hendelseProdusent.clear()
            service.sendAktuellePåminnelser(ikkePassert1.plusMinutes(1).inOsloAsInstant())
            assertEquals(1, hendelseProdusent.hendelser.size)
            assertEquals(uuid("4"), hendelseProdusent.hendelser.first().aggregateId)
        }

}

private fun HendelseModel.OppgaveOpprettet.medPåminnelse(
    tidspunkt: LocalDateTime,
    uuid: UUID = notifikasjonId
) = copy(
    notifikasjonId = uuid,
    hendelseId = uuid,
    påminnelse = HendelseModel.Påminnelse(
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
            tidspunkt,
            opprettetTidspunkt,
            frist,
            null
        ),
        eksterneVarsler = listOf(
            HendelseModel.SmsVarselKontaktinfo(
                varselId = uuid("3"),
                fnrEllerOrgnr = "1",
                tlfnr = "1",
                smsTekst = "hey",
                sendevindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            HendelseModel.EpostVarselKontaktinfo(
                varselId = uuid("4"),
                fnrEllerOrgnr = "1",
                epostAddr = "1",
                tittel = "hey",
                htmlBody = "body",
                sendevindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
        )
    ),
)
