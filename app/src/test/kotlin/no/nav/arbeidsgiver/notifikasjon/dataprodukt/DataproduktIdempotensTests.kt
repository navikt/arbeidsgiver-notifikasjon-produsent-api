package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.typeNavn
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class DataproduktIdempotensTests : DescribeSpec({
    val database = testDatabase(Dataprodukt.databaseConfig)
    val repository = DataproduktModel(database)

    val metadata = HendelseMetadata(Instant.now())

    val mottakere = listOf(
        HendelseModel.AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )
    )
    val opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00")

    val sak = HendelseModel.SakOpprettet(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("1"),
        grupperingsid = "1",
        merkelapp = "tag",
        mottakere = mottakere,
        tittel = "tjohei",
        lenke = "#foo",
        oppgittTidspunkt = opprettetTidspunkt,
        mottattTidspunkt = opprettetTidspunkt,
        hardDelete = null,
    )

    val oppgaveKnyttetTilSak = HendelseModel.SakOpprettet(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("1"),
        grupperingsid = "1",
        merkelapp = "tag",
        mottakere = mottakere,
        tittel = "tjohei",
        lenke = "#foo",
        oppgittTidspunkt = null,
        mottattTidspunkt = opprettetTidspunkt,
        hardDelete = null,
    )

    val oppgaveUtenGrupperingsid = HendelseModel.OppgaveOpprettet(
        hendelseId = UUID.randomUUID(),
        notifikasjonId = UUID.randomUUID(),
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

    val oppgaveMedGrupperingsidMedAnnenTag = HendelseModel.OppgaveOpprettet(
        hendelseId = UUID.randomUUID(),
        notifikasjonId = UUID.randomUUID(),
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

    describe("Dataprodukt Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse, metadata)
            repository.oppdaterModellEtterHendelse(hendelse, metadata)
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                repository.oppdaterModellEtterHendelse(
                    EksempelHendelse.HardDelete.copy(
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        aggregateId = hendelse.aggregateId,
                    ), metadata
                )
                repository.oppdaterModellEtterHendelse(hendelse, metadata)
            }
        }
    }

    describe("Atomisk sletting av Sak sletter kun tilhørende notifikasjoner") {
        repository.oppdaterModellEtterHendelse(sak, metadata)
        repository.oppdaterModellEtterHendelse(oppgaveKnyttetTilSak, metadata)
        repository.oppdaterModellEtterHendelse(oppgaveUtenGrupperingsid, metadata)
        repository.oppdaterModellEtterHendelse(oppgaveMedGrupperingsidMedAnnenTag, metadata)
        repository.oppdaterModellEtterHendelse(
            HendelseModel.HardDelete(
                virksomhetsnummer = sak.virksomhetsnummer,
                aggregateId = sak.aggregateId,
                hendelseId = sak.hendelseId,
                produsentId = sak.produsentId,
                kildeAppNavn = sak.kildeAppNavn,
                deletedAt = OffsetDateTime.now(),
                grupperingsid = sak.grupperingsid,
                merkelapp = sak.merkelapp,
            ), metadata
        )

        val notifikasjoner = database.nonTransactionalExecuteQuery(
            """
                select notifikasjon_id from notifikasjon
            """.trimIndent(),
            transform = { getObject("notifikasjon_id", UUID::class.java) }
        )

        notifikasjoner shouldContainExactlyInAnyOrder listOf(
            oppgaveUtenGrupperingsid.aggregateId,
            oppgaveMedGrupperingsidMedAnnenTag.aggregateId
        )
    }
})


