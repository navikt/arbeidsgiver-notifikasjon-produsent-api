package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.*
import java.sql.Connection
import java.time.LocalDate
import java.util.*

class SkedulertUtgåttRepository : AutoCloseable {
    private val database = EphemeralDatabase("skedulert_utgatt",
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
    override fun close() = database.close()

    class SkedulertUtgått(
        val oppgaveId: UUID,
        val frist: LocalDate,
        val virksomhetsnummer: String,
        val produsentId: String,
    )

    fun hentOgFjernAlleMedFrist(localDateNow: LocalDate): Collection<SkedulertUtgått> {
        return database.useTransaction {
            executeQuery(
                """
                    delete from oppgaver
                    where frist < ?
                    returning *
                """.trimIndent(),
                setup = {
                    setLocalDate(localDateNow)
                },
                result = {
                    val alleUtgåtte = mutableListOf<SkedulertUtgått>()
                    while (next()) {
                        alleUtgåtte.add(
                            SkedulertUtgått(
                                oppgaveId = getUUID("oppgave_id"),
                                frist = getLocalDate("frist"),
                                virksomhetsnummer = getString("virksomhetsnummer"),
                                produsentId = getString("produsent_id"),
                            )
                        )
                    }
                    alleUtgåtte
                }
            )
        }
    }


    private fun Connection.erOppgaveSlettet(oppgaveId: UUID): Boolean {
        return executeQuery(
            """
                select true as slettet
                from slettede_oppgaver
                where oppgave_id = ?
                limit 1
            """.trimIndent(),
            setup = {
                setUUID(oppgaveId)
            },
            result = {
                if (next()) getBoolean("slettet") else false
            }
        )
    }

    private fun Connection.erSakForOppgaveSlettet(oppgaveId: UUID): Boolean {
        return executeQuery(
            """
                select true as slettet
                from oppgave_sak_kobling
                join slettede_saker using (sak_id)
                where oppgave_sak_kobling.oppgave_id = ?
                limit 1
            """.trimIndent(),
            setup = {
                setUUID(oppgaveId)
            },
            result = {
                if (next()) getBoolean("slettet") else false
            }
        )
    }

    private fun Connection.upsertOppgaveFrist(skedulertUtgått: SkedulertUtgått) {
        executeUpdate(
            """
                insert into oppgaver(oppgave_id, frist, virksomhetsnummer, produsent_id)
                values (?, ?, ?, ?)
                on conflict (oppgave_id) do update set
                    frist = excluded.frist,
                    virksomhetsnummer = excluded.virksomhetsnummer,
                    produsent_id = excluded.produsent_id
            """.trimIndent()
        ) {
            setUUID(skedulertUtgått.oppgaveId)
            setLocalDate(skedulertUtgått.frist)
            setText(skedulertUtgått.virksomhetsnummer)
            setText(skedulertUtgått.produsentId)
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
        database.useTransaction {
            executeUpdate(
                """
                    delete from oppgaver
                    where oppgave_id = ? and frist <= ?
                """.trimIndent()
            ) {
                setUUID(oppgaveId)
                setLocalDate(utgaattTidspunkt)
            }
        }
    }

    fun slettOppgave(aggregateId: UUID) {
        database.useTransaction {
            this@useTransaction.slettOppgave(aggregateId = aggregateId)
        }
    }

    private fun Connection.slettOppgave(aggregateId: UUID) {
        executeUpdate(
            """
                delete from oppgaver
                where oppgave_id = ?
            """.trimIndent()
        ) {
            setUUID(aggregateId)
        }
    }

    private fun Connection.huskSlettetOppgave(aggregateId: UUID) {
        executeUpdate(
            """
                insert into slettede_oppgaver(oppgave_id)
                values (?)
                on conflict (oppgave_id) do nothing
            """.trimIndent()
        ) {
            setUUID(aggregateId)
        }
    }

    private fun Connection.huskSlettetSak(
        grupperingsid: String,
        merkelapp: String,
        sakId: UUID,
    ) {
        executeUpdate(
            """
                insert into slettede_saker(grupperingsid, merkelapp, sak_id)
                values (?, ?, ?)
                on conflict (sak_id) do nothing
            """.trimIndent()
        ) {
            setText(grupperingsid)
            setText(merkelapp)
            setUUID(sakId)
        }
    }

    private fun Connection.slettOppgaverKnyttetTilSak(sakId: UUID) {
        executeUpdate(
            """
                delete from oppgaver
                where oppgave_id in (
                    select oppgave_sak_kobling.oppgave_id
                    from oppgave_sak_kobling
                    where sak_id = ?
                )
            """.trimIndent()
        ) {
            setUUID(sakId)
        }
    }

    fun huskSakOppgaveKobling(sakId: UUID, oppgaveId: UUID) {
        database.useTransaction {
            executeUpdate(
                """
                    insert into oppgave_sak_kobling (oppgave_id, sak_id) values (?, ?)
                    on conflict (oppgave_id) do update
                    set sak_id = excluded.sak_id
                """.trimIndent()
            ) {
                setUUID(oppgaveId)
                setUUID(sakId)
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
