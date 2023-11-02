package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.*
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.Oppgavetilstand.*
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.util.*

private typealias OppgaveId = UUID
private typealias BestillingHendelseId = UUID

enum class Oppgavetilstand {
    UTFØRT, NY, UTGÅTT
}

class SkedulertPåminnelseRepository : AutoCloseable {
    data class SkedulertPåminnelse(
        val oppgaveId: OppgaveId,
        val fristOpprettetTidspunkt: Instant,
        val frist: LocalDate?,
        val tidspunkt: HendelseModel.PåminnelseTidspunkt,
        val eksterneVarsler: List<HendelseModel.EksterntVarsel>,
        val virksomhetsnummer: String,
        val produsentId: String,
        val bestillingHendelseId: BestillingHendelseId,
    )

    private val database = EphemeralDatabase("skedulert_paaminnelse",
        """
        create table paaminnelser (
            oppgave_id text primary key,
            frist_opprettet_tidspunkt text not null,
            frist text null,
            paaminnelsestidspunkt text not null,
            tidspunkt_json text not null,
            eksterne_varsler_json text not null,
            virksomhetsnummer text not null,
            produsent_id text not null,
            bestilling_hendelseid text not null
        );
        
        create index paaminnelser_paaminnelsestidspunkt_idx on paaminnelser(paaminnelsestidspunkt);
        
        create table oppgavetilstand (
            oppgave_id text primary key,
            tilstand text not null
        );
        """.trimIndent()
    )

    override fun close() = database.close()

    fun hentOgFjernAlleAktuellePåminnelser(now: Instant): Collection<SkedulertPåminnelse> {
        return database.useConnection {
            executeQuery(
                """
                    delete from paaminnelser
                    where paaminnelsestidspunkt <= ?
                    returning *
                """.trimIndent(),
                setup = {
                    setInstant(now)
                },
                result = {
                    val resultat = mutableListOf<SkedulertPåminnelse>()
                    while (next()) {
                        resultat.add(SkedulertPåminnelse(
                            oppgaveId = getUUID("oppgave_id"),
                            fristOpprettetTidspunkt = getInstant("frist_opprettet_tidspunkt"),
                            frist = getLocalDateOrNull("frist"),
                            tidspunkt = getJson("tidspunkt_json"),
                            eksterneVarsler = getJson("eksterne_varsler_json"),
                            virksomhetsnummer = getString("virksomhetsnummer"),
                            produsentId = getString("produsent_id"),
                            bestillingHendelseId = getUUID("bestilling_hendelseid"),
                        ))
                    }
                    resultat
                }
            )
        }
    }

    fun processHendelse(hendelse: HendelseModel.Hendelse) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> database.useTransaction {
                settOppgavetilstand(hendelse.notifikasjonId, NY)

                bestillPåminnelse(
                    hendelse = hendelse,
                    påminnelse = hendelse.påminnelse,
                    fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    frist = hendelse.frist,
                )
            }

            is HendelseModel.FristUtsatt -> database.useTransaction {
                kansellerAllePåminnelserForOppgave(oppgaveId = hendelse.notifikasjonId)
                val oppgavetilstand = hentOppgavetilstand(hendelse.notifikasjonId)
                when (oppgavetilstand) {
                    NY -> {
                        bestillPåminnelse(
                            hendelse = hendelse,
                            påminnelse = hendelse.påminnelse,
                            frist = hendelse.frist,
                            fristOpprettetTidspunkt = hendelse.fristEndretTidspunkt,
                        )
                    }

                    UTGÅTT -> {
                        settOppgavetilstand(hendelse.notifikasjonId, NY)
                        bestillPåminnelse(
                            hendelse = hendelse,
                            påminnelse = hendelse.påminnelse,
                            frist = hendelse.frist,
                            fristOpprettetTidspunkt = hendelse.fristEndretTidspunkt,
                        )
                    }

                    UTFØRT,
                    null -> {
                        /* noop */
                    }
                }
            }

            is HendelseModel.PåminnelseOpprettet -> database.useConnection {
                kansellerBestilltPåminnelse(bestillingId = hendelse.bestillingHendelseId)
            }

            is HendelseModel.OppgaveUtført -> database.useTransaction {
                settOppgavetilstand(hendelse.notifikasjonId, UTFØRT)
                kansellerAllePåminnelserForOppgave(hendelse.notifikasjonId)
            }

            is HendelseModel.OppgaveUtgått -> database.useTransaction {
                settOppgavetilstand(hendelse.notifikasjonId, UTGÅTT)
                kansellerAllePåminnelserForOppgave(hendelse.notifikasjonId)
            }

            is HendelseModel.SoftDelete,
            is HendelseModel.HardDelete -> database.useTransaction {
                slettOppgavetilstand(hendelse.aggregateId)
                kansellerAllePåminnelserForOppgave(hendelse.aggregateId)
            }

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    private fun Connection.settOppgavetilstand(oppgaveId: OppgaveId, tilstand: Oppgavetilstand) {
        executeUpdate("""
            insert into oppgavetilstand (oppgave_id, tilstand) values (?, ?)
            on conflict (oppgave_id) do update 
            set tilstand = excluded.tilstand
        """.trimIndent()
        ) {
            setUUID(oppgaveId)
            setEnum(tilstand)
        }
    }

    private fun Connection.hentOppgavetilstand(oppgaveId: UUID): Oppgavetilstand? {
        return executeQuery(
            """
            select * from oppgavetilstand where oppgave_id = ?
            """.trimIndent(),
            setup = {
                setUUID(oppgaveId)
            },
            result = {
                if (next()) getEnum<Oppgavetilstand>("tilstand") else null
            }
        )
    }

    private fun Connection.slettOppgavetilstand(oppgaveId: UUID) {
        executeUpdate(
            """
            delete from oppgavetilstand where oppgave_id = ?
            """.trimIndent()
        ) {
            setUUID(oppgaveId)
        }
    }


    private fun Connection.bestillPåminnelse(
        hendelse: HendelseModel.Hendelse,
        påminnelse: HendelseModel.Påminnelse?,
        fristOpprettetTidspunkt: Instant,
        frist: LocalDate?,
    ) {
        if (påminnelse == null) {
            return
        }

        executeUpdate(
            """
            insert into paaminnelser (
                oppgave_id,
                frist_opprettet_tidspunkt,
                frist,
                tidspunkt_json,
                eksterne_varsler_json,
                virksomhetsnummer,
                produsent_id,
                bestilling_hendelseid,
                paaminnelsestidspunkt
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (oppgave_id) do nothing
            """.trimIndent()
        ) {
            setUUID(hendelse.aggregateId)
            setInstant(fristOpprettetTidspunkt)
            setLocalDateOrNull(frist)
            setJson(påminnelse.tidspunkt)
            setJson(påminnelse.eksterneVarsler)
            setText(hendelse.virksomhetsnummer)
            setTextOrNull(hendelse.produsentId)
            setUUID(hendelse.hendelseId)
            setInstant(påminnelse.tidspunkt.påminnelseTidspunkt)
        }
    }

    private fun Connection.kansellerAllePåminnelserForOppgave(oppgaveId: OppgaveId) {
        executeUpdate(
            """
                delete from paaminnelser where oppgave_id = ?
            """.trimIndent()
        ) {
            setUUID(oppgaveId)
        }
    }


    private fun Connection.kansellerBestilltPåminnelse(bestillingId: BestillingHendelseId) {
        executeUpdate(
            """
                delete from paaminnelser where bestilling_hendelseid = ?
            """.trimIndent()
        ) {
            setUUID(bestillingId)
        }
    }
}

