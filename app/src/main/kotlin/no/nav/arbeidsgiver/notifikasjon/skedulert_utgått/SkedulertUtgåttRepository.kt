package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.hendelse.HardDeletedRepository
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SkedulertUtgåttRepository(
    private val database: Database
) : HardDeletedRepository(database) {
    class Oppgave(
        val oppgaveId: UUID,
        val frist: LocalDate,
        val virksomhetsnummer: String,
        val produsentId: String,
    )

    class Kalenderavtale(
        val kalenderavtaleId: UUID,
        val startTidspunkt: LocalDateTime,
        val tilstand: KalenderavtaleTilstand,
        val virksomhetsnummer: String,
        val produsentId: String,
        val merkelapp: String,
        val grupperingsid: String,
        val opprettetTidspunkt: Instant,
    )

    enum class AggregatType {
        OPPGAVE,
        KALENDERAVTALE,
    }

    private val log = logger()

    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse) {
        if (hendelse is HendelseModel.AggregatOpprettet) {
            registrerKoblingForCascadeDelete(hendelse)
        }
        if (erHardDeleted(hendelse.aggregateId)) {
            log.info("skipping harddeleted event {}", hendelse)
            return
        }

        when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> {
                /* Vi må huske saks-id uavhengig av om det er frist på oppgaven, for
                 * det kan komme en frist senere. */
                if (hendelse.sakId != null) {
                    huskSakOppgaveKobling(
                        sakId = hendelse.sakId,
                        oppgaveId = hendelse.aggregateId
                    )
                }

                if (hendelse.frist != null) {
                    upsertSkedulertUtgåttOppgave(
                        Oppgave(
                            oppgaveId = hendelse.notifikasjonId,
                            frist = hendelse.frist,
                            virksomhetsnummer = hendelse.virksomhetsnummer,
                            produsentId = hendelse.produsentId,
                        )
                    )
                }
            }

            is HendelseModel.KalenderavtaleOpprettet -> {
                huskSakKalenderavtaleKobling(
                    sakId = hendelse.sakId,
                    kalenderavtaleId = hendelse.aggregateId
                )

                skedulerAvholdtKalenderavtale(
                    Kalenderavtale(
                        kalenderavtaleId = hendelse.notifikasjonId,
                        startTidspunkt = hendelse.startTidspunkt,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentId = hendelse.produsentId,
                        tilstand = hendelse.tilstand,
                        merkelapp = hendelse.merkelapp,
                        grupperingsid = hendelse.grupperingsid,
                        opprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    )
                )
            }

            is HendelseModel.KalenderavtaleOppdatert -> {
                when (hendelse.tilstand) {
                    null -> {
                        // tilstand er uendret, skedulering forblir som den er
                        return
                    }

                    KalenderavtaleTilstand.AVLYST,
                    KalenderavtaleTilstand.AVHOLDT -> {
                        // kalenderavtale er avlyst eller avholdt, slett skedulering
                        database.transaction {
                            slettSkedulertUtgått(hendelse.notifikasjonId)
                        }
                    }

                    else -> {
                        // kalenderavtale er endret til en annen åpen tilstand, skeduler avholdt på nytt
                        oppdaterSkedulerAvholdtKalenderavtale(hendelse.notifikasjonId)
                    }
                }
            }

            is HendelseModel.FristUtsatt -> {
                upsertSkedulertUtgåttOppgave(
                    Oppgave(
                        oppgaveId = hendelse.notifikasjonId,
                        frist = hendelse.frist,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentId = hendelse.produsentId,
                    )
                )
            }

            is HendelseModel.OppgaveUtgått ->
                slettSkeduleringHvisOppgaveErEldre(
                    oppgaveId = hendelse.aggregateId,
                    utgaattTidspunkt = hendelse.utgaattTidspunkt.asOsloLocalDate()
                )

            is HendelseModel.OppgaveUtført ->
                database.transaction {
                    slettOppgave(aggregateId = hendelse.aggregateId)
                }

            is HendelseModel.SoftDelete,
            is HendelseModel.HardDelete -> {
                database.transaction {
                    slett(
                        aggregateId = hendelse.aggregateId,
                        grupperingsid = when (hendelse) {
                            is HendelseModel.SoftDelete -> hendelse.grupperingsid
                            is HendelseModel.HardDelete -> hendelse.grupperingsid
                            else -> throw IllegalStateException("Uventet hendelse $hendelse")
                        },
                        merkelapp = when (hendelse) {
                            is HendelseModel.SoftDelete -> hendelse.merkelapp
                            is HendelseModel.HardDelete -> hendelse.merkelapp
                            else -> throw IllegalStateException("Uventet hendelse $hendelse")
                        },
                    )
                    registrerDelete(this, hendelse.aggregateId)
                }
            }

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.NesteStegSak,
            is HendelseModel.TilleggsinformasjonSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselKansellert,
            is HendelseModel.OppgavePåminnelseEndret,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    suspend fun hentOgFjernAlleUtgåtteOppgaver(
        localDateNow: LocalDate,
        action: suspend (Oppgave) -> Unit
    ) = database.transaction {
        executeQuery(
            """
                delete from skedulert_utgatt as s
                    using oppgave as o
                where o.oppgave_id = s.aggregat_id
                    and s.aggregat_type = ? and o.frist < ?
                returning *
            """, {
                text(AggregatType.OPPGAVE.name)
                localDateAsText(localDateNow)
            }) {
            Oppgave(
                oppgaveId = getUUID("oppgave_id"),
                frist = getLocalDate("frist"),
                virksomhetsnummer = getString("virksomhetsnummer"),
                produsentId = getString("produsent_id"),
            )
        }.forEach {
            action(it)
        }
    }


    suspend fun hentOgFjernAlleAvholdteKalenderavtaler(
        localDateTimeNow: LocalDateTime,
        action: suspend (Kalenderavtale) -> Unit
    ) =
        database.transaction {
            executeQuery(
                """
                delete from skedulert_utgatt as s
                    using kalenderavtale as k
                where k.kalenderavtale_id = s.aggregat_id
                    and s.aggregat_type = ? and k.start_tidspunkt < ?
                returning *
            """, {
                    text(AggregatType.KALENDERAVTALE.name)
                    localDateTimeAsText(localDateTimeNow)
                }) {
                Kalenderavtale(
                    kalenderavtaleId = getUUID("kalenderavtale_id"),
                    startTidspunkt = getLocalDateTime("start_tidspunkt"),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    tilstand = getString("tilstand").let { KalenderavtaleTilstand.valueOf(it) },
                    produsentId = getString("produsent_id"),
                    merkelapp = getString("merkelapp"),
                    grupperingsid = getString("grupperingsid"),
                    opprettetTidspunkt = Instant.parse(
                        getString("opprettet_tidspunkt"),
                    )
                )
            }.forEach {
                action(it)
            }
        }

    private suspend fun upsertSkedulertUtgåttOppgave(oppgave: Oppgave) = database.transaction {
        executeUpdate(
            """
                insert into oppgave(oppgave_id, frist, virksomhetsnummer, produsent_id)
                values (?, ?, ?, ?)
                on conflict (oppgave_id) do update set
                    frist = excluded.frist,
                    virksomhetsnummer = excluded.virksomhetsnummer,
                    produsent_id = excluded.produsent_id
            """.trimIndent()
        ) {
            uuid(oppgave.oppgaveId)
            localDateAsText(oppgave.frist)
            text(oppgave.virksomhetsnummer)
            text(oppgave.produsentId)
        }
        executeUpdate(
            """
                insert into skedulert_utgatt(aggregat_id, aggregat_type)
                values (?, ?)
                on conflict (aggregat_id) do nothing
            """.trimIndent()
        ) {
            uuid(oppgave.oppgaveId)
            text(AggregatType.OPPGAVE.name)
        }
    }

    private suspend fun skedulerAvholdtKalenderavtale(
        kalenderavtale: Kalenderavtale
    ) = database.transaction {
        executeUpdate(
            """
                insert into kalenderavtale(
                    kalenderavtale_id, start_tidspunkt, tilstand, virksomhetsnummer, produsent_id, merkelapp, grupperingsid, opprettet_tidspunkt
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (kalenderavtale_id) do update set
                    start_tidspunkt = excluded.start_tidspunkt,
                    tilstand = excluded.tilstand,
                    virksomhetsnummer = excluded.virksomhetsnummer,
                    produsent_id = excluded.produsent_id,
                    merkelapp = excluded.merkelapp,
                    grupperingsid = excluded.grupperingsid,
                    opprettet_tidspunkt = excluded.opprettet_tidspunkt
            """.trimIndent()
        ) {
            uuid(kalenderavtale.kalenderavtaleId)
            localDateTimeAsText(kalenderavtale.startTidspunkt)
            text(kalenderavtale.tilstand.name)
            text(kalenderavtale.virksomhetsnummer)
            text(kalenderavtale.produsentId)
            text(kalenderavtale.merkelapp)
            text(kalenderavtale.grupperingsid)
            text(kalenderavtale.opprettetTidspunkt.toString())
        }
        when (kalenderavtale.tilstand) {
            KalenderavtaleTilstand.AVHOLDT,
            KalenderavtaleTilstand.AVLYST -> {
                // sluttilstand ingen behov for skedulert overgang
            }

            else -> {
                executeUpdate(
                    """
                        insert into skedulert_utgatt(aggregat_id, aggregat_type)
                        values (?, ?)
                        on conflict (aggregat_id) do nothing
                    """.trimIndent()
                ) {
                    uuid(kalenderavtale.kalenderavtaleId)
                    text(AggregatType.KALENDERAVTALE.name)
                }
            }
        }
    }

    private suspend fun oppdaterSkedulerAvholdtKalenderavtale(
        kalenderavtaleId: UUID
    ) = database.transaction {

        executeUpdate(
            """
                insert into skedulert_utgatt(aggregat_id, aggregat_type)
                select ?, ?
                where exists (
                    select 1 from kalenderavtale
                    where kalenderavtale_id = ?
                )
                on conflict (aggregat_id) do nothing
            """.trimIndent()
        ) {
            uuid(kalenderavtaleId)
            text(AggregatType.KALENDERAVTALE.name)
            uuid(kalenderavtaleId)
        }
    }

    suspend fun slettSkeduleringHvisOppgaveErEldre(oppgaveId: UUID, utgaattTidspunkt: LocalDate) =
        database.nonTransactionalExecuteUpdate(
            """
                delete from skedulert_utgatt
                    using skedulert_utgatt as s
                left join oppgave as o on s.aggregat_id = o.oppgave_id
                    where s.aggregat_type = ?
                    and o.oppgave_id = ?
                    and o.frist <= ?
            """.trimIndent()
        ) {
            text(AggregatType.OPPGAVE.name)
            uuid(oppgaveId)
            localDateAsText(utgaattTidspunkt)
        }

    fun Transaction.slett(
        aggregateId: UUID,
        grupperingsid: String?,
        merkelapp: String?,
    ) {
        if (grupperingsid != null && merkelapp != null) {
            slettOppgaverKnyttetTilSak(sakId = aggregateId)
            slettKalenderavtalerKnyttetTilSak(sakId = aggregateId)
        } else {
            slettOppgave(aggregateId = aggregateId)
            slettKalenderavtale(aggregateId = aggregateId)
        }
    }

    private fun Transaction.slettOppgaverKnyttetTilSak(sakId: UUID) {
        executeUpdate(
            """
                delete from oppgave
                where oppgave_id in (
                    select oppgave_sak_kobling.oppgave_id
                    from oppgave_sak_kobling
                    where sak_id = ?
                )
            """
        ) {
            uuid(sakId)
        }
        executeUpdate(
            """
                delete from skedulert_utgatt
                where aggregat_id in (
                    select oppgave_sak_kobling.oppgave_id
                    from oppgave_sak_kobling
                    where sak_id = ?
                )
            """
        ) {
            uuid(sakId)
        }
    }

    private fun Transaction.slettKalenderavtalerKnyttetTilSak(sakId: UUID) {
        executeUpdate(
            """
                delete from kalenderavtale
                where kalenderavtale_id in (
                    select kalenderavtale_sak_kobling.kalenderavtale_id
                    from kalenderavtale_sak_kobling
                    where sak_id = ?
                )
            """.trimIndent()
        ) {
            uuid(sakId)
        }

        executeUpdate(
            """
                delete from skedulert_utgatt
                where aggregat_id in (
                    select kalenderavtale_sak_kobling.kalenderavtale_id
                    from kalenderavtale_sak_kobling
                    where sak_id = ?
                )
            """
        ) {
            uuid(sakId)
        }
    }

    fun Transaction.slettOppgave(aggregateId: UUID) {
        executeUpdate(
            """
                delete from oppgave
                where oppgave_id = ?
            """.trimIndent()
        ) {
            uuid(aggregateId)
        }
        slettSkedulertUtgått(aggregateId = aggregateId)
    }

    fun Transaction.slettKalenderavtale(aggregateId: UUID) {
        executeUpdate(
            """
                delete from kalenderavtale
                where kalenderavtale_id = ?
            """.trimIndent()
        ) {
            uuid(aggregateId)
        }
        slettSkedulertUtgått(aggregateId = aggregateId)
    }

    fun Transaction.slettSkedulertUtgått(aggregateId: UUID) {
        executeUpdate(
            """
                delete from skedulert_utgatt
                where aggregat_id = ?
            """.trimIndent()
        ) {
            uuid(aggregateId)
        }
    }

    suspend fun huskSakOppgaveKobling(sakId: UUID, oppgaveId: UUID) {
        database.nonTransactionalExecuteUpdate(
            """
                insert into oppgave_sak_kobling (oppgave_id, sak_id) values (?, ?)
                on conflict (oppgave_id) do update
                set sak_id = excluded.sak_id
            """.trimIndent()
        ) {
            uuid(oppgaveId)
            uuid(sakId)
        }
    }


    suspend fun huskSakKalenderavtaleKobling(sakId: UUID, kalenderavtaleId: UUID) {
        database.nonTransactionalExecuteUpdate(
            """
                        insert into kalenderavtale_sak_kobling (kalenderavtale_id, sak_id) values (?, ?)
                        on conflict (kalenderavtale_id) do update
                        set sak_id = excluded.sak_id
                    """.trimIndent()
        ) {
            uuid(kalenderavtaleId)
            uuid(sakId)
        }
    }
}
