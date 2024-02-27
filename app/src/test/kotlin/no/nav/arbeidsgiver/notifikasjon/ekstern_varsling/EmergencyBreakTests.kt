package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime

class EmergencyBreakTests : DescribeSpec({

    val oppgaveOpprettet = OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("1"),
        produsentId = "",
        kildeAppNavn = "",
        merkelapp = "",
        eksternId = "",
        mottakere = listOf(AltinnMottaker(
            virksomhetsnummer = "",
            serviceCode = "",
            serviceEdition = "",
        )),
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


    describe("EmergencyBreak") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        val emergencyBreak = repository.emergencyBreakOn()

        it("should be enabled on start up") {
            emergencyBreak shouldBe true
        }


        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        database.nonTransactionalExecuteUpdate("""
            update emergency_break
            set stop_processing = false
            where id = 0
        """)

        repository.detectEmptyDatabase()

        val emergencyBreak2 = repository.emergencyBreakOn()

        it("should not be enabled on non-empty database") {
            emergencyBreak2 shouldBe false
        }
    }
})