package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class IdempotentOppgaveOpprettetTests: DescribeSpec({

    val smsVarsel = SmsVarselKontaktinfo(
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

    describe("mutual exclusive access to ekstern_varsel") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        it("ligger 1 job og venter") {
            database.jobQueue() shouldContainExactly listOf(uuid("1"))
        }

        it("ingen duplikat av varsel") {
            database.antallVarsler(uuid("1")) shouldBe 1
        }
    }
})


suspend fun Database.antallVarsler(varselId: UUID): Int =
    this.nonTransactionalExecuteQuery("""
        select count(*) as antall 
        from ekstern_varsel_kontaktinfo 
        where varsel_id = ?
    """, {
        uuid(varselId)
    }
    ) {
        getInt("antall")
    }.first()
