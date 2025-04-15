package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class IdempotentOppgaveOpprettetTest {

    private val smsVarsel = SmsVarselKontaktinfo(
        varselId = uuid("1"),
        tlfnr = "",
        fnrEllerOrgnr = "",
        smsTekst = "",
        sendevindu = EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
        sendeTidspunkt = null
    )

    val oppgaveOpprettet = OppgaveOpprettet(
        virksomhetsnummer = "",
        notifikasjonId = uuid("0"),
        hendelseId = uuid("0"),
        produsentId = "",
        kildeAppNavn = "",
        merkelapp = "",
        eksternId = "",
        mottakere = listOf(AltinnMottaker("", "", "")),
        tekst = "",
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+01"),
        eksterneVarsler = listOf(smsVarsel),
        grupperingsid = null,
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )

    @Test
    fun `mutual exclusive access to ekstern_varsel`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        // ligger 1 job og venter
        assertEquals(listOf(uuid("1")), database.jobQueue())

        // ingen duplikat av varsel
        assertEquals(1, database.antallVarsler(uuid("1")))
    }
}


suspend fun Database.antallVarsler(varselId: UUID): Int =
    this.nonTransactionalExecuteQuery(
        """
        select count(*) as antall 
        from ekstern_varsel_kontaktinfo 
        where varsel_id = ?
    """, {
            uuid(varselId)
        }
    ) {
        getInt("antall")
    }.first()
