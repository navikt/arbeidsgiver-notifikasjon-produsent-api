package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.EphemeralDatabase
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.useExecuteQuery
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.usePrepareStatement
import java.sql.Connection
import java.time.LocalDate
import java.util.*

class SkedulertUtgåttRepository : AutoCloseable {
    private val database = EphemeralDatabase("skedulert_utgatt")
    override fun close() = database.close()

    init {
        database.useConnection {
            createStatement().use {
                it.executeUpdate(
                    """
                    create table oppgaver (
                        oppgave_id text not null primary key,
                        frist text not null,
                        virksomhetsnummer text not null,
                        produsent_id text not null
                    );
                    
                    create index oppgaver_frist_idx on oppgaver(frist);
                    
                    create table oppgave_sak_kobling (
                        oppgave_id text not null primary key,
                        sak_id text not null
                    );
                    
                    create table slettede_oppgaver (
                        oppgave_id text not null primary key
                    );
                    
                    create table slettede_saker (
                        sak_id text not null,
                        grupperingsid text not null,
                        merkelapp text not null,
                        constraint slettede_saker_pk primary key (sak_id)
                    );
                    """.trimIndent()
                )
            }
        }
    }


    class SkedulertUtgått(
        val oppgaveId: UUID,
        val frist: LocalDate,
        val virksomhetsnummer: String,
        val produsentId: String,
    )

    fun hentOgFjernAlleMedFrist(localDateNow: LocalDate): Collection<SkedulertUtgått> {
        val alleUtgåtte = mutableListOf<SkedulertUtgått>()

        database.useConnection {
            usePrepareStatement(
                """
                delete from oppgaver
                        where frist < ?
                        returning *
                """.trimIndent()
            ) {
                setString(1, localDateNow.toString())

                useExecuteQuery {
                    while (next()) {
                        alleUtgåtte.add(SkedulertUtgått(
                            oppgaveId = UUID.fromString(getString("oppgave_id")),
                            frist = LocalDate.parse(getString("frist")),
                            virksomhetsnummer = getString("virksomhetsnummer"),
                            produsentId = getString("produsent_id"),
                        ))
                    }
                }
            }
        }

        return alleUtgåtte
    }


    private fun Connection.erOppgaveSlettet(oppgaveId: UUID): Boolean {
        return usePrepareStatement(
            """
                select true as slettet
                from slettede_oppgaver
                where oppgave_id = ?
                limit 1
            """.trimIndent()
        ) {
            setString(1, oppgaveId.toString())
            useExecuteQuery {
                if (next()) getBoolean("slettet") else false
            }
        }
    }

    private fun Connection.erSakForOppgaveSlettet(oppgaveId: UUID): Boolean {
        return usePrepareStatement("""
                select true as slettet
                from oppgave_sak_kobling
                join slettede_saker using (sak_id)
                where oppgave_sak_kobling.oppgave_id = ?
                limit 1
        """.trimIndent()
        ) {
            setString(1, oppgaveId.toString())
            useExecuteQuery {
                if (next()) getBoolean("slettet") else false
            }
        }
    }

    private fun Connection.upsertOppgaveFrist(skedulertUtgått: SkedulertUtgått) {
        usePrepareStatement(
            """
                insert into oppgaver(oppgave_id, frist, virksomhetsnummer, produsent_id)
                values (?, ?, ?, ?)
                on conflict (oppgave_id) do update set
                    frist = excluded.frist,
                    virksomhetsnummer = excluded.virksomhetsnummer,
                    produsent_id = excluded.produsent_id
            """.trimIndent()
        ) {
            setString(1, skedulertUtgått.oppgaveId.toString())
            setString(2, skedulertUtgått.frist.toString())
            setString(3, skedulertUtgått.virksomhetsnummer)
            setString(4, skedulertUtgått.produsentId)
            execute()
        }
    }

    fun skedulerUtgått(skedulertUtgått: SkedulertUtgått) {
        database.useTransaction {
            if (erOppgaveSlettet(oppgaveId = skedulertUtgått.oppgaveId)) {
                return@useTransaction
            }
            if (erSakForOppgaveSlettet(oppgaveId = skedulertUtgått.oppgaveId)) {
                return@useTransaction
            }
            upsertOppgaveFrist(skedulertUtgått)
        }
    }


    fun slettOmEldre(oppgaveId: UUID, utgaattTidspunkt: LocalDate) {
        database.useConnection {
            usePrepareStatement(
                """
                delete from oppgaver
                where oppgave_id = ? and frist <= ?
                """.trimIndent()
            ) {
                setString(1, oppgaveId.toString())
                setString(2, utgaattTidspunkt.toString())
                execute()
            }
        }
    }

    fun slettOppgave(aggregateId: UUID) {
        database.useConnection {
            this@useConnection.slettOppgave(aggregateId = aggregateId)
        }
    }

    private fun Connection.slettOppgave(aggregateId: UUID) {
        usePrepareStatement(
            """
                delete from oppgaver
                where oppgave_id = ?
                """.trimIndent()
        ) {
            setString(1, aggregateId.toString())
            execute()
        }
    }


    private fun Connection.huskSlettetOppgave(aggregateId: UUID) {
        usePrepareStatement(
            """
            insert into slettede_oppgaver(oppgave_id)
            values (?)
            on conflict (oppgave_id) do nothing
            """.trimIndent()
        ) {
            setString(1, aggregateId.toString())
            execute()
        }
    }

    private fun Connection.huskSlettetSak(
        grupperingsid: String,
        merkelapp: String,
        sakId: UUID,
    ) {
        usePrepareStatement(
            """
                insert into slettede_saker(grupperingsid, merkelapp, sak_id)
                values (?, ?, ?)
                on conflict (sak_id) do nothing
            """.trimIndent()
        ) {
            setString(1, grupperingsid)
            setString(2, merkelapp)
            setString(3, sakId.toString())
            execute()
        }

    }

    private fun Connection.slettOppgaverKnyttetTilSak(sakId: UUID) {
        usePrepareStatement("""
                delete from oppgaver
                where oppgave_id in (
                    select oppgave_sak_kobling.oppgave_id
                    from oppgave_sak_kobling
                    where sak_id = ?
                )
            """.trimIndent()
        ) {
            setString(1, sakId.toString())
            execute()
        }
    }

    fun huskSakOppgaveKobling(sakId: UUID, oppgaveId: UUID) {
        database.useConnection {
            usePrepareStatement("""
                insert into oppgave_sak_kobling (oppgave_id, sak_id) values (?, ?)
                on conflict (oppgave_id) do update
                set sak_id = excluded.sak_id
            """.trimIndent()
            ) {
                setString(1, oppgaveId.toString())
                setString(2, sakId.toString())
                execute()
            }
        }
    }

    fun slettOgHuskSlett(
        aggregateId: UUID,
        grupperingsid: String?,
        merkelapp: String?,
    ) {
        database.useTransaction {
            if (grupperingsid != null && merkelapp != null) {
                huskSlettetSak(
                    grupperingsid = grupperingsid,
                    merkelapp = merkelapp,
                    sakId = aggregateId,
                )
                slettOppgaverKnyttetTilSak(sakId = aggregateId)
            } else {
                huskSlettetOppgave(aggregateId = aggregateId)
                slettOppgave(aggregateId = aggregateId)
            }
        }
    }
}
