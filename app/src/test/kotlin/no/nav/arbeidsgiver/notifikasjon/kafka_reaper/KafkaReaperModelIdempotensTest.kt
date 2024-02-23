package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class KafkaReaperModelIdempotensTest : DescribeSpec({

    val mottakere = listOf(
        HendelseModel.AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )
    )
    val opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00")

    val sak = SakOpprettet(
        hendelseId = uuid("010"),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("010"),
        grupperingsid = "1",
        merkelapp = "tag",
        mottakere = mottakere,
        tittel = "tjohei",
        lenke = "#foo",
        oppgittTidspunkt = opprettetTidspunkt,
        mottattTidspunkt = opprettetTidspunkt,
        hardDelete = null,
    )

    val oppgaveKnyttetTilSak = OppgaveOpprettet(
        notifikasjonId = uuid("001"),
        hendelseId = uuid("001"),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("010"),
        grupperingsid = "1",
        merkelapp = "tag",
        mottakere = mottakere,
        lenke = "#foo",
        hardDelete = null,
        eksternId = "1",
        eksterneVarsler = listOf(),
        opprettetTidspunkt = opprettetTidspunkt,
        tekst = "tjohei",
        frist = null,
        påminnelse = null,
    )

    val oppgaveUtenGrupperingsid = OppgaveOpprettet(
        hendelseId = uuid("002"),
        notifikasjonId = uuid("002"),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        grupperingsid = null,
        sakId = null,
        merkelapp = "tag",
        mottakere = mottakere,
        tekst = "tjohei",
        lenke = "#foo",
        opprettetTidspunkt = opprettetTidspunkt,
        eksternId = "1",
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = null,
    )

    val oppgaveMedGrupperingsidMedAnnenTag = OppgaveOpprettet(
        hendelseId = uuid("003"),
        notifikasjonId = uuid("003"),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        grupperingsid = "1",
        sakId = null,
        merkelapp = "tag2",
        mottakere = mottakere,
        tekst = "tjohei",
        lenke = "#foo",
        opprettetTidspunkt = opprettetTidspunkt,
        eksternId = "1",
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = null,
    )

    describe("Kafka Reaper Idempotent oppførsel") {
        val database = testDatabase(KafkaReaper.databaseConfig)
        val model = KafkaReaperModelImpl(database)
        withData(EksempelHendelse.Alle) { hendelse ->
            model.oppdaterModellEtterHendelse(hendelse)
            model.oppdaterModellEtterHendelse(hendelse)
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                val database = testDatabase(KafkaReaper.databaseConfig)
                val model = KafkaReaperModelImpl(database)

                model.oppdaterModellEtterHendelse(EksempelHendelse.HardDelete.copy(
                    virksomhetsnummer = hendelse.virksomhetsnummer,
                    aggregateId = hendelse.aggregateId,
                ))
                model.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }

    describe("Atomisk hard delete av Sak sletter kun tilhørende notifikasjoner") {
        val database = testDatabase(KafkaReaper.databaseConfig)
        val model = KafkaReaperModelImpl(database)
        model.oppdaterModellEtterHendelse(sak)
        model.oppdaterModellEtterHendelse(oppgaveKnyttetTilSak)
        model.oppdaterModellEtterHendelse(oppgaveUtenGrupperingsid)
        model.oppdaterModellEtterHendelse(oppgaveMedGrupperingsidMedAnnenTag)
        model.oppdaterModellEtterHendelse(
            HardDelete(
                virksomhetsnummer = sak.virksomhetsnummer,
                aggregateId = sak.aggregateId,
                hendelseId = uuid("999"),
                produsentId = sak.produsentId,
                kildeAppNavn = sak.kildeAppNavn,
                deletedAt = OffsetDateTime.now(),
                grupperingsid = sak.grupperingsid,
                merkelapp = sak.merkelapp,
            )
        )
        val notifikasjoner = database.nonTransactionalExecuteQuery(
            """
                select notifikasjon_id from deleted_notifikasjon
            """.trimIndent(),
            transform = { getObject("notifikasjon_id", UUID::class.java) }
        )

        notifikasjoner shouldContainExactlyInAnyOrder listOf(
            sak.aggregateId,
            oppgaveKnyttetTilSak.aggregateId
        )
    }
})