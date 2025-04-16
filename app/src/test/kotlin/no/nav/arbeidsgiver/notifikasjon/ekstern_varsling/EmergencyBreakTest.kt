package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class EmergencyBreakTest {

    val oppgaveOpprettet = OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("1"),
        produsentId = "",
        kildeAppNavn = "",
        merkelapp = "",
        eksternId = "",
        mottakere = listOf(
            AltinnMottaker(
                virksomhetsnummer = "",
                serviceCode = "",
                serviceEdition = "",
            )
        ),
        tekst = "",
        grupperingsid = "",
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
        eksterneVarsler = listOf(
            SmsVarselKontaktinfo(
                varselId = uuid("2"),
                tlfnr = "",
                fnrEllerOrgnr = "",
                smsTekst = "",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
            )
        ),
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )


    @Test
    fun EmergencyBreak() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        val emergencyBreak = repository.emergencyBreakOn()

        // should be enabled on start up
        assertEquals(true, emergencyBreak)


        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        database.nonTransactionalExecuteUpdate(
            """
            update emergency_break
            set stop_processing = false
            where id = 0
        """
        )

        repository.detectEmptyDatabase()

        val emergencyBreak2 = repository.emergencyBreakOn()

        // should not be enabled on non-empty database
        assertEquals(false, emergencyBreak2)
    }
}