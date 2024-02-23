package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.kotest.assertions.throwables.shouldNotThrowAny
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

    val oppgaveKnyttetTilSak = HendelseModel.OppgaveOpprettet(
        notifikasjonId = uuid("001"),
        hendelseId = uuid("001"),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("1"),
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

    val oppgaveUtenGrupperingsid = HendelseModel.OppgaveOpprettet(
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

    val oppgaveMedGrupperingsidMedAnnenTag = HendelseModel.OppgaveOpprettet(
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

    describe("Dataprodukt Idempotent oppførsel") {
        val database = testDatabase(Dataprodukt.databaseConfig)
        val repository = DataproduktModel(database)

        withData(EksempelHendelse.Alle) { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse, metadata)
            repository.oppdaterModellEtterHendelse(hendelse, metadata)
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                val database = testDatabase(Dataprodukt.databaseConfig)
                val repository = DataproduktModel(database)

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

    describe("Atomisk hard delete av Sak sletter kun tilhørende notifikasjoner") {
        val database = testDatabase(Dataprodukt.databaseConfig)
        val repository = DataproduktModel(database)

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


    describe("Atomisk soft delete av Sak sletter kun tilhørende notifikasjoner") {
        val database = testDatabase(Dataprodukt.databaseConfig)
        val repository = DataproduktModel(database)

        repository.oppdaterModellEtterHendelse(sak, metadata)
        repository.oppdaterModellEtterHendelse(oppgaveKnyttetTilSak, metadata)
        repository.oppdaterModellEtterHendelse(oppgaveUtenGrupperingsid, metadata)
        repository.oppdaterModellEtterHendelse(oppgaveMedGrupperingsidMedAnnenTag, metadata)
        repository.oppdaterModellEtterHendelse(
            HendelseModel.SoftDelete(
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
                where soft_deleted_tidspunkt is null
            """.trimIndent(),
            transform = { getObject("notifikasjon_id", UUID::class.java) }
        )

        notifikasjoner shouldContainExactlyInAnyOrder listOf(
            oppgaveUtenGrupperingsid.aggregateId,
            oppgaveMedGrupperingsidMedAnnenTag.aggregateId
        )
    }

    describe("Pseudonymisering av tegn som kan tolkes som escape characters ") {
        val database = testDatabase(Dataprodukt.databaseConfig)
        val repository = DataproduktModel(database)

        val hendelse = EksempelHendelse.SakOpprettet.copy(tittel = """Billakkerer\Hjelpearbeider""")
        it("Skal ikke feile fordi det blir tolket") {
            shouldNotThrowAny {
                repository.oppdaterModellEtterHendelse(hendelse, metadata)
            }
        }
    }
})


