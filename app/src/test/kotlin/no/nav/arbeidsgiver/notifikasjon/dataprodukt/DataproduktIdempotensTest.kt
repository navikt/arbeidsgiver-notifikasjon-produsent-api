package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_MOTTAKER_1
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_MOTTAKER_2
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_VIRKSOMHET_1
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.asMap
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.intellij.lang.annotations.Language
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DataproduktIdempotensTest {
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
        nesteSteg = "Neste steg",
        hardDelete = null,
        tilleggsinformasjon = null
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

    @Test
    fun `Dataprodukt Idempotent oppførsel`() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
        val repository = DataproduktModel(database)

        EksempelHendelse.Alle.forEach { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse, metadata)
            repository.oppdaterModellEtterHendelse(hendelse, metadata)
        }
    }

    @Test
    fun `Håndterer partial replay hvor midt i hendelsesforløp etter harddelete`() {
        EksempelHendelse.Alle.forEach { hendelse ->
            withTestDatabase(Dataprodukt.databaseConfig) { database ->
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

    @Test
    fun `Atomisk hard delete av Sak sletter kun tilhørende notifikasjoner`() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
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

        assertEquals(
            listOf(
                oppgaveUtenGrupperingsid.aggregateId,
                oppgaveMedGrupperingsidMedAnnenTag.aggregateId
            ),
            notifikasjoner
        )
    }


    @Test
    fun `Atomisk soft delete av Sak sletter kun tilhørende notifikasjoner`() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
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

        assertEquals(
            listOf(
                oppgaveUtenGrupperingsid.aggregateId,
                oppgaveMedGrupperingsidMedAnnenTag.aggregateId
            ),
            notifikasjoner
        )
    }

    @Test
    fun `Pseudonymisering av tegn som kan tolkes som escape characters `() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
        val repository = DataproduktModel(database)

        val hendelse = EksempelHendelse.SakOpprettet.copy(tittel = """Billakkerer\Hjelpearbeider""")
        // Skal ikke feile fordi det blir tolket
        repository.oppdaterModellEtterHendelse(hendelse, metadata)
    }

    @Test
    fun `hard delete på sak sletter alt og kan replayes`() = withTestDatabase(Dataprodukt.databaseConfig) { database ->
        val repository = DataproduktModel(database)
        val merkelapp = "idempotenstest"
        val grupperingsid = "gr-42"
        val sakId = uuid("42")
        val notifikasjonId = uuid("314")

        val hendelsesforløp = listOf(
            EksempelHendelse.SakOpprettet.copy(
                hendelseId = sakId,
                sakId = sakId,
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
            ).also {
                repository.oppdaterModellEtterHendelse(it, metadata)
            },
            EksempelHendelse.BeskjedOpprettet.copy(
                hendelseId = notifikasjonId,
                notifikasjonId = notifikasjonId,
                sakId = sakId,
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
            ).also {
                repository.oppdaterModellEtterHendelse(it, metadata)
            },
            HendelseModel.HardDelete(
                hendelseId = UUID.randomUUID(),
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                aggregateId = sakId,
                produsentId = "",
                kildeAppNavn = "",
                deletedAt = OffsetDateTime.now(),
                grupperingsid = grupperingsid,
                merkelapp = merkelapp,
            ).also {
                repository.oppdaterModellEtterHendelse(it, metadata)
            }
        )

        assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)

        // replay events
        hendelsesforløp.forEach {
            repository.oppdaterModellEtterHendelse(it, metadata)
        }

        assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)
    }
}

private suspend fun assertDeleted(
    database: Database,
    sakId: UUID,
    notifikasjonId: UUID,
    grupperingsid: String,
    merkelapp: String
) {
    with(database) {
        // sak is deleted
        assertNoRows("select * from sak where sak_id = '${sakId}'")

        // notifikasjon is deleted
        assertNoRows("select * from notifikasjon where grupperingsid = '${grupperingsid}' and merkelapp = '${merkelapp}'")

        // mottaker sak is deleted
        assertNoRows("select * from mottaker_enkeltrettighet where notifikasjon_id = '${sakId}'")
        assertNoRows("select * from mottaker_altinn_ressurs where notifikasjon_id = '${sakId}'")

        // mottaker notifikasjon is deleted
        assertNoRows("select * from mottaker_enkeltrettighet where notifikasjon_id = '${notifikasjonId}'")
        assertNoRows("select * from mottaker_altinn_ressurs where notifikasjon_id = '${notifikasjonId}'")
    }

}

private suspend fun Database.assertNoRows(@Language("PostgreSQL") sql: String) {
    with(
        nonTransactionalExecuteQuery(sql) { asMap() }
    ) {
        assertEquals(0, size)
    }
}


