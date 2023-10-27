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
                """.trimIndent()
                )

                it.executeUpdate(
                    """
                    create table slettede_oppgaver (
                        oppgave_id text not null primary key
                    );
                """.trimIndent()
                )
                it.executeUpdate(
                    """
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


    private fun Connection.erSlettet(oppgaveId: UUID): Boolean {
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

    private fun Connection.upsertOppgave(skedulertUtgått: SkedulertUtgått) {
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
            if (erSlettet(skedulertUtgått.oppgaveId)) {
                return@useTransaction
            }
            upsertOppgave(skedulertUtgått)
        }
    }


    fun slettOmEldre(aggregateId: UUID, utgaattTidspunkt: LocalDate) {
        database.useConnection {
            usePrepareStatement(
                """
                delete from oppgaver
                where oppgave_id = ? and frist <= ?
                """.trimIndent()
            ) {
                setString(1, aggregateId.toString())
                setString(2, utgaattTidspunkt.toString())
                execute()
            }
        }
    }


    fun slett(id: UUID) {
        database.useConnection {
            usePrepareStatement(
                """
                delete from oppgaver
                where oppgave_id = ?
                """.trimIndent()
            ) {
                setString(1, id.toString())
                execute()
            }
        }
    }


    fun huskSlettetOppgave(id: UUID) {
        database.useConnection {
            usePrepareStatement(
                """
                insert into slettede_oppgaver(oppgave_id)
                values (?)
                on conflict (oppgave_id) do nothing
                """.trimIndent()
            ) {
                setString(1, id.toString())
                execute()
            }
        }
    }

    fun huskSlettetSak(
        grupperingsid: String,
        merkelapp: String,
        sakId: UUID,
    ) {
        database.useConnection {
            usePrepareStatement("""
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
    }
}
