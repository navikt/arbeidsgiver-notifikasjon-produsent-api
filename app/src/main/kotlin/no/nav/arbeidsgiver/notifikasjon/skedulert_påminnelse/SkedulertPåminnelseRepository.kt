package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.Notifikasjontilstand.*
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

class SkedulertPåminnelseRepository(
    private val database: Database
) : AutoCloseable {
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

    override fun close() = database.close()

    suspend fun hentOgFjernAlleAktuellePåminnelser(now: Instant): Collection<SkedulertPåminnelse> {
        return database.transaction {
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
                    instantAsText(now)
                },
                transform = {
                    SkedulertPåminnelse(
                        notifikasjonId = getUuid("notifikasjon_id"),
                        hendelseOpprettetTidspunkt = getInstant("frist_opprettet_tidspunkt"),
                        frist = getLocalDateOrNull("frist"),
                        startTidspunkt = getLocalDateTimeOrNull("start_tidspunkt"),
                        tidspunkt = getJson("tidspunkt_json"),
                        eksterneVarsler = getJson("eksterne_varsler_json"),
                        virksomhetsnummer = getString("virksomhetsnummer"),
                        produsentId = getString("produsent_id"),
                        bestillingHendelseId = getUuid("bestilling_id"),
                    )
                }
            )

            executeBatch(
                """
                    insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
                """.trimIndent(),
                bestillinger,
            ) {
                uuid(it.bestillingHendelseId)
            }

            return@transaction bestillinger
        }
    }

    suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> database.transaction {
                executeUpdate(
                    """
                insert into notifikasjoner (notifikasjon_id, merkelapp, tilstand, grupperingsid)
                values (?, ?, ?, ?)
                on conflict (notifikasjon_id) do nothing
                """.trimIndent()
                ) {
                    uuid(hendelse.aggregateId)
                    text(hendelse.merkelapp)
                    enumAsText(NOTIFIKASJON_AKTIV)
                    nullableText(hendelse.grupperingsid)
                }

                bestillPåminnelse(
                    hendelse = hendelse,
                    påminnelse = hendelse.påminnelse,
                    fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    frist = hendelse.frist,
                    startTidspunkt = null,
                )
            }

            is HendelseModel.FristUtsatt -> database.transaction {
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

            is HendelseModel.PåminnelseOpprettet -> database.transaction {
                markerBestillingLukket(bestillingId = hendelse.bestillingHendelseId)
            }

            is HendelseModel.OppgaveUtført -> database.transaction {
                settNotifikasjontilstand(hendelse.notifikasjonId, OPPGAVE_UTFØRT)
            }

            is HendelseModel.OppgaveUtgått -> database.transaction {
                markerPåminnelserLukket(notifikasjonId = hendelse.notifikasjonId)
            }

            is HendelseModel.SoftDelete -> database.transaction {
                processDelete(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.HardDelete -> database.transaction {
                processDelete(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.KalenderavtaleOpprettet -> database.transaction {
                executeUpdate(
                    """
                insert into notifikasjoner (notifikasjon_id, merkelapp, tilstand, grupperingsid)
                values (?, ?, ?, ?)
                on conflict (notifikasjon_id) do nothing
                """.trimIndent()
                ) {
                    uuid(hendelse.aggregateId)
                    text(hendelse.merkelapp)
                    enumAsText(NOTIFIKASJON_AKTIV)
                    nullableText(hendelse.grupperingsid)
                }

                bestillPåminnelse(
                    hendelse = hendelse,
                    påminnelse = hendelse.påminnelse,
                    startTidspunkt = hendelse.startTidspunkt,
                    fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    frist = null,
                )
            }

            is HendelseModel.KalenderavtaleOppdatert -> database.transaction {
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

            is HendelseModel.OppgavePåminnelseEndret -> database.transaction {
                markerPåminnelserLukket(notifikasjonId = hendelse.notifikasjonId)
                when (hentOppgavetilstand(hendelse.notifikasjonId)) {
                    NOTIFIKASJON_AKTIV -> {
                        bestillPåminnelse(
                            hendelse = hendelse,
                            påminnelse = hendelse.påminnelse,
                            frist = hendelse.frist,
                            startTidspunkt = null,
                            /**
                             * I mangel av at vi ikke har noen god måte å vite når eksisterende frist ble opprettet, i tillegg til
                             * at feltet fristOpprettetTidspunkt ikke ser ut til å blir brukt noe sted, har vi valgt å sette dette feltet til tidspunktet når oppgaven ble opprettet.
                             * Dersom man ønsker å sette dette korrekt, vil en løsning være å persistere fristOpprettetTidspunkt i produsent apiet, og populere det gjennom OppgavePåminnelseEndret hendelsen.
                             */
                            fristOpprettetTidspunkt = hendelse.oppgaveOpprettetTidspunkt,
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

            is HendelseModel.NesteStegSak,
            is HendelseModel.TilleggsinformasjonSak,
            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselKansellert,
            is HendelseModel.EksterntVarselVellykket -> Unit

        }
    }

    private fun Transaction.processDelete(
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

    private fun Transaction.markerPåminnelserLukket(notifikasjonId: UUID) {
        val slettedeBestillingsIder = executeQuery(
            """
                delete from bestillinger
                where notifikasjon_id = ?
                returning bestilling_id
            """.trimIndent(),
            setup = {
                uuid(notifikasjonId)
            },
            transform = {
                getUuid("bestilling_id")
            }
        )

        executeBatch(
            """
                insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
            """.trimIndent(),
            slettedeBestillingsIder,
        ) {
            uuid(it)
        }
    }

    private fun Transaction.markerBestillingLukket(bestillingId: UUID) {
        executeUpdate(
            """
                delete from bestillinger
                where bestilling_id = ?
            """.trimIndent()
        ) {
            uuid(bestillingId)
        }

        executeUpdate(
            """
                insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
            """.trimIndent(),
        ) {
            uuid(bestillingId)
        }
    }

    private fun Transaction.processSakSlettet(merkelapp: String, grupperingsid: String) {
        executeUpdate(
            """
                update notifikasjoner
                set tilstand = ?
                where merkelapp = ? and grupperingsid = ?
            """.trimIndent()
        ) {
            enumAsText(NOTIFIKASJON_SLETTET)
            text(merkelapp)
            text(grupperingsid)
        }

        executeUpdate(
            """
                insert into slettede_saker(merkelapp, grupperingsid)
                values (?, ?)
                on conflict do nothing
            """.trimIndent()
        ) {
            text(merkelapp)
            text(grupperingsid)
        }
    }

    private fun Transaction.settNotifikasjontilstand(notifikasjonId: NotifikasjonId, tilstand: Notifikasjontilstand) {
        executeUpdate(
            """
                update notifikasjoner
                set tilstand = case when tilstand = ? then tilstand else ? end
                where notifikasjon_id = ?
            """.trimIndent()
        ) {
            enumAsText(NOTIFIKASJON_SLETTET)
            enumAsText(tilstand)
            uuid(notifikasjonId)
        }
    }

    private fun Transaction.hentOppgavetilstand(oppgaveId: UUID): Notifikasjontilstand? {
        return executeQuery(
            """
            select tilstand from notifikasjoner where notifikasjon_id = ?
            """.trimIndent(),
            setup = {
                uuid(oppgaveId)
            },
            transform = {
                getEnum<Notifikasjontilstand>("tilstand")
            }
        ).firstOrNull()
    }

    private fun Transaction.bestillPåminnelse(
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
            uuid(hendelse.aggregateId)
            instantAsText(fristOpprettetTidspunkt)
            nullableLocalDateAsText(frist)
            nullableLocalDateTimeAsText(startTidspunkt)
            jsonb(påminnelse.tidspunkt)
            jsonb(påminnelse.eksterneVarsler)
            text(hendelse.virksomhetsnummer)
            nullableText(hendelse.produsentId)
            uuid(hendelse.hendelseId)
            instantAsText(påminnelse.tidspunkt.påminnelseTidspunkt)
        }
    }
}

