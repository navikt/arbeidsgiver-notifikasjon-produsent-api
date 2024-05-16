package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.*
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.Notifikasjontilstand.*
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private typealias NotifikasjonId = UUID
private typealias BestillingHendelseId = UUID

enum class Notifikasjontilstand {
    NOTIFIKASJON_AKTIV,
    OPPGAVE_UTFØRT,
    KALENDERAVTALE_AVLYST,
    NOTIFIKASJON_SLETTET,
}

class SkedulertPåminnelseRepository : AutoCloseable {
    data class SkedulertPåminnelse(
        val notifikasjonId: NotifikasjonId,
        val hendelseOpprettetTidspunkt: Instant,
        val frist: LocalDate?,
        val startTidspunkt: LocalDateTime?,
        val tidspunkt: HendelseModel.PåminnelseTidspunkt,
        val eksterneVarsler: List<HendelseModel.EksterntVarsel>,
        val virksomhetsnummer: String,
        val produsentId: String,
        val bestillingHendelseId: BestillingHendelseId,
    )

    private val database = EphemeralDatabase("skedulert_paaminnelse",
        """
        create table notifikasjoner (
            notifikasjon_id text not null primary key,
            tilstand text not null,
            merkelapp text not null,
            grupperingsid text
        );
        
        create index notifikasjoner_koordinat_idx on notifikasjoner(merkelapp, grupperingsid);
        
        create table slettede_saker(
            merkelapp text not null,
            grupperingsid text not null,
            primary key (merkelapp, grupperingsid)
        );
        
        -- utførte (inkludert avbrutte) bestillinger
        create table utforte_bestillinger(
            bestilling_id text not null primary key
        );
        
        create table bestillinger(
            bestilling_id text not null primary key,
            paaminnelsestidspunkt text not null,
            notifikasjon_id text references notifikasjoner(notifikasjon_id),
            frist_opprettet_tidspunkt text not null,
            frist text null,
            start_tidspunkt text null,
            tidspunkt_json text not null,
            eksterne_varsler_json text not null,
            virksomhetsnummer text not null,
            produsent_id text not null
        );
        
        create index bestillinger_utsendelse_idx on bestillinger(paaminnelsestidspunkt);
        create index bestillinger_notifikasjon_id_idx on bestillinger(notifikasjon_id); 
        """.trimIndent()
    )

    override fun close() = database.close()

    fun hentOgFjernAlleAktuellePåminnelser(now: Instant): Collection<SkedulertPåminnelse> {
        return database.useTransaction {
            val bestillinger = executeQuery(
                """
                    delete from bestillinger
                    where 
                        paaminnelsestidspunkt <= ?
                        and notifikasjon_id in (
                            select notifikasjon_id
                            from notifikasjoner
                            left join slettede_saker on (
                                notifikasjoner.merkelapp = slettede_saker.merkelapp
                                and notifikasjoner.grupperingsid = slettede_saker.grupperingsid
                            )
                            where slettede_saker.grupperingsid is null and tilstand = '$NOTIFIKASJON_AKTIV' 
                        )
                    returning *
                """.trimIndent(),
                setup = {
                    setInstant(now)
                },
                result = {
                    val resultat = mutableListOf<SkedulertPåminnelse>()
                    while (next()) {
                        resultat.add(
                            SkedulertPåminnelse(
                                notifikasjonId = getUUID("notifikasjon_id"),
                                hendelseOpprettetTidspunkt = getInstant("frist_opprettet_tidspunkt"),
                                frist = getLocalDateOrNull("frist"),
                                startTidspunkt = getLocalDateTimeOrNull("start_tidspunkt"),
                                tidspunkt = getJson("tidspunkt_json"),
                                eksterneVarsler = getJson("eksterne_varsler_json"),
                                virksomhetsnummer = getString("virksomhetsnummer"),
                                produsentId = getString("produsent_id"),
                                bestillingHendelseId = getUUID("bestilling_id"),
                            )
                        )
                    }
                    resultat
                }
            )

            executeBatch(
                """
                    insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
                """.trimIndent(),
                bestillinger,
            ) {
                setUUID(it.bestillingHendelseId)
            }

            return@useTransaction bestillinger
        }
    }

    fun processHendelse(hendelse: HendelseModel.Hendelse) {
        when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> database.useTransaction {
                executeUpdate(
                    """
                insert into notifikasjoner (notifikasjon_id, merkelapp, tilstand, grupperingsid)
                values (?, ?, ?, ?)
                on conflict (notifikasjon_id) do nothing
                """.trimIndent()
                ) {
                    setUUID(hendelse.aggregateId)
                    setText(hendelse.merkelapp)
                    setEnum(NOTIFIKASJON_AKTIV)
                    setTextOrNull(hendelse.grupperingsid)
                }

                bestillPåminnelse(
                    hendelse = hendelse,
                    påminnelse = hendelse.påminnelse,
                    fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    frist = hendelse.frist,
                    startTidspunkt = null,
                )
            }

            is HendelseModel.FristUtsatt -> database.useTransaction {
                markerPåminnelserLukket(notifikasjonId = hendelse.notifikasjonId)
                when (hentOppgavetilstand(hendelse.notifikasjonId)) {
                    NOTIFIKASJON_AKTIV -> {
                        bestillPåminnelse(
                            hendelse = hendelse,
                            påminnelse = hendelse.påminnelse,
                            frist = hendelse.frist,
                            startTidspunkt = null,
                            fristOpprettetTidspunkt = hendelse.fristEndretTidspunkt,
                        )
                    }

                    OPPGAVE_UTFØRT,
                    KALENDERAVTALE_AVLYST,
                    NOTIFIKASJON_SLETTET,
                    null -> {
                        /* noop */
                    }
                }
            }

            is HendelseModel.PåminnelseOpprettet -> database.useTransaction {
                markerBestillingLukket(bestillingId = hendelse.bestillingHendelseId)
            }

            is HendelseModel.OppgaveUtført -> database.useTransaction {
                settNotifikasjontilstand(hendelse.notifikasjonId, OPPGAVE_UTFØRT)
            }

            is HendelseModel.OppgaveUtgått -> database.useTransaction {
                markerPåminnelserLukket(notifikasjonId = hendelse.notifikasjonId)
            }

            is HendelseModel.SoftDelete -> database.useTransaction {
                processDelete(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.HardDelete -> database.useTransaction {
                processDelete(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.KalenderavtaleOpprettet -> database.useTransaction {
                executeUpdate(
                    """
                insert into notifikasjoner (notifikasjon_id, merkelapp, tilstand, grupperingsid)
                values (?, ?, ?, ?)
                on conflict (notifikasjon_id) do nothing
                """.trimIndent()
                ) {
                    setUUID(hendelse.aggregateId)
                    setText(hendelse.merkelapp)
                    setEnum(NOTIFIKASJON_AKTIV)
                    setTextOrNull(hendelse.grupperingsid)
                }

                bestillPåminnelse(
                    hendelse = hendelse,
                    påminnelse = hendelse.påminnelse,
                    startTidspunkt = hendelse.startTidspunkt,
                    fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    frist = null,
                )
            }

            is HendelseModel.KalenderavtaleOppdatert -> database.useTransaction {
                if (hendelse.tilstand == KalenderavtaleTilstand.AVLYST) {
                    settNotifikasjontilstand(hendelse.notifikasjonId, KALENDERAVTALE_AVLYST)
                    markerPåminnelserLukket(notifikasjonId = hendelse.notifikasjonId)
                }

                if (hendelse.startTidspunkt != null) {
                    /**
                     * I skrivende stund er det er ikke mulig å endre starttidspunkt på en kalenderavtale.
                     * Dersom vi åpner for det må det dokumenteres i spesifikasjonen at eksisterende påminnelser blir kansellert,
                     * og at man må angi nye dersom man ønsker det.
                     */
                    markerPåminnelserLukket(notifikasjonId = hendelse.notifikasjonId)
                }

                if (hendelse.påminnelse != null) {
                    markerPåminnelserLukket(notifikasjonId = hendelse.notifikasjonId)
                    bestillPåminnelse(
                        hendelse = hendelse,
                        påminnelse = hendelse.påminnelse,
                        startTidspunkt = hendelse.startTidspunkt, // kan være null, representerer kun det evt. endrede tidspunktet
                        fristOpprettetTidspunkt = hendelse.opprettetTidspunkt,
                        frist = null,
                    )
                }
            }
            is HendelseModel.NesteStegSak -> TODO()

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselKansellert,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    private fun Connection.processDelete(
        aggregateId: UUID,
        merkelapp: String?,
        grupperingsid: String?,
    ) {
        if (merkelapp != null && grupperingsid != null) {
            processSakSlettet(merkelapp, grupperingsid)
        } else {
            settNotifikasjontilstand(aggregateId, NOTIFIKASJON_SLETTET)
        }
    }

    private fun Connection.markerPåminnelserLukket(notifikasjonId: UUID) {
        val slettedeBestillingsIder = executeQuery(
            """
                delete from bestillinger
                where notifikasjon_id = ?
                returning bestilling_id
            """.trimIndent(),
            setup = {
                setUUID(notifikasjonId)
            },
            result = {
                resultAsList {
                    getUUID("bestilling_id")
                }
            }
        )

        executeBatch(
            """
                insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
            """.trimIndent(),
            slettedeBestillingsIder,
        ) {
            setUUID(it)
        }
    }

    private fun Connection.markerBestillingLukket(bestillingId: UUID) {
        executeUpdate(
            """
                delete from bestillinger
                where bestilling_id = ?
            """.trimIndent()
        ) {
            setUUID(bestillingId)
        }

        executeUpdate(
            """
                insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
            """.trimIndent(),
        ) {
            setUUID(bestillingId)
        }
    }

    private fun Connection.processSakSlettet(merkelapp: String, grupperingsid: String) {
        executeUpdate(
            """
                update notifikasjoner
                set tilstand = ?
                where merkelapp = ? and grupperingsid = ?
            """.trimIndent()
        ) {
            setEnum(NOTIFIKASJON_SLETTET)
            setText(merkelapp)
            setText(grupperingsid)
        }

        executeUpdate(
            """
                insert into slettede_saker(merkelapp, grupperingsid)
                values (?, ?)
                on conflict do nothing
            """.trimIndent()
        ) {
            setText(merkelapp)
            setText(grupperingsid)
        }
    }

    private fun Connection.oppgaveOpprettet(oppgaveOpprettet: HendelseModel.OppgaveOpprettet) {
        executeUpdate(
            """
                insert into notifikasjoner (notifikasjon_id, merkelapp, tilstand, grupperingsid)
                values (?, ?, ?, ?)
                on conflict (notifikasjon_id) do nothing
            """.trimIndent()
        ) {
            setUUID(oppgaveOpprettet.aggregateId)
            setText(oppgaveOpprettet.merkelapp)
            setEnum(NOTIFIKASJON_AKTIV)
            setTextOrNull(oppgaveOpprettet.grupperingsid)
        }
    }

    private fun Connection.settNotifikasjontilstand(notifikasjonId: NotifikasjonId, tilstand: Notifikasjontilstand) {
        executeUpdate(
            """
                update notifikasjoner
                set tilstand = case when tilstand = ? then tilstand else ? end
                where notifikasjon_id = ?
            """.trimIndent()
        ) {
            setEnum(NOTIFIKASJON_SLETTET)
            setEnum(tilstand)
            setUUID(notifikasjonId)
        }
    }

    private fun Connection.hentOppgavetilstand(oppgaveId: UUID): Notifikasjontilstand? {
        return executeQuery(
            """
            select tilstand from notifikasjoner where notifikasjon_id = ?
            """.trimIndent(),
            setup = {
                setUUID(oppgaveId)
            },
            result = {
                if (next()) getEnum<Notifikasjontilstand>("tilstand") else null
            }
        )
    }

    private fun Connection.bestillPåminnelse(
        hendelse: HendelseModel.Hendelse,
        påminnelse: HendelseModel.Påminnelse?,
        fristOpprettetTidspunkt: Instant,
        frist: LocalDate?,
        startTidspunkt: LocalDateTime?,
    ) {
        if (påminnelse == null) {
            return
        }

        executeUpdate(
            """
            insert into bestillinger (
                notifikasjon_id,
                frist_opprettet_tidspunkt,
                frist,
                start_tidspunkt,
                tidspunkt_json,
                eksterne_varsler_json,
                virksomhetsnummer,
                produsent_id,
                bestilling_id,
                paaminnelsestidspunkt
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (bestilling_id) do nothing
            """.trimIndent()
        ) {
            setUUID(hendelse.aggregateId)
            setInstant(fristOpprettetTidspunkt)
            setLocalDateOrNull(frist)
            setLocalDateTimeOrNull(startTidspunkt)
            setJson(påminnelse.tidspunkt)
            setJson(påminnelse.eksterneVarsler)
            setText(hendelse.virksomhetsnummer)
            setTextOrNull(hendelse.produsentId)
            setUUID(hendelse.hendelseId)
            setInstant(påminnelse.tidspunkt.påminnelseTidspunkt)
        }
    }
}

