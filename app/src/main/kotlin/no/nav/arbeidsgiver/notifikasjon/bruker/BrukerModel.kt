package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.time.OffsetDateTime
import java.util.*

interface BrukerModel {
    data class Tilgang(
        val virksomhet: String,
        val servicecode: String,
        val serviceedition: String,
    )

    sealed interface Notifikasjon {
        val id: UUID
    }

    data class Beskjed(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
        override val id: UUID,
        val klikketPaa: Boolean
    ) : Notifikasjon

    data class Oppgave(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
        override val id: UUID,
        val klikketPaa: Boolean,
        val tilstand: Tilstand,
    ) : Notifikasjon {
        @Suppress("unused")
        /* leses fra database */
        enum class Tilstand {
            NY,
            UTFOERT
        }
    }

    suspend fun hentNotifikasjoner(
        fnr: String,
        tilganger: Collection<Tilgang>,
        ansatte: List<NærmesteLederService.NærmesteLederFor>
    ): List<Notifikasjon>

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse)
    suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String?
}

class BrukerModelImpl(
    private val database: Database
) : BrukerModel {
    private val log = logger()

    private fun Hendelse.BeskjedOpprettet.tilQueryDomene(): BrukerModel.Beskjed =
        BrukerModel.Beskjed(
            id = this.id,
            merkelapp = this.merkelapp,
            tekst = this.tekst,
            grupperingsid = this.grupperingsid,
            lenke = this.lenke,
            eksternId = this.eksternId,
            mottaker = this.mottaker,
            opprettetTidspunkt = this.opprettetTidspunkt,
            klikketPaa = false /* TODO: lag QueryBeskjedMedKlikk, så denne linjen kan fjernes */
        )

    private fun Hendelse.OppgaveOpprettet.tilQueryDomene(): BrukerModel.Oppgave =
        BrukerModel.Oppgave(
            id = this.id,
            merkelapp = this.merkelapp,
            tekst = this.tekst,
            grupperingsid = this.grupperingsid,
            lenke = this.lenke,
            eksternId = this.eksternId,
            mottaker = this.mottaker,
            opprettetTidspunkt = this.opprettetTidspunkt,
            tilstand = BrukerModel.Oppgave.Tilstand.NY,
            klikketPaa = false /* TODO: lag QueryBeskjedMedKlikk, så denne linjen kan fjernes */
        )

    val BrukerModel.Notifikasjon.mottaker: Mottaker
        get() = when (this) {
            is BrukerModel.Oppgave -> this.mottaker
            is BrukerModel.Beskjed -> this.mottaker
        }


    private val timer = Health.meterRegistry.timer("query_model_repository_hent_notifikasjoner")

    override suspend fun hentNotifikasjoner(
        fnr: String,
        tilganger: Collection<BrukerModel.Tilgang>,
        ansatte: List<NærmesteLederService.NærmesteLederFor>
    ): List<BrukerModel.Notifikasjon> = timer.coRecord {
        val tilgangerJsonB = tilganger.joinToString {
            "'${
                objectMapper.writeValueAsString(
                    AltinnMottaker(
                        it.servicecode,
                        it.serviceedition,
                        it.virksomhet
                    )
                )
            }'"
        }

        val ansatteLookupTable = ansatte.toSet()

        val notifikasjoner = database.runNonTransactionalQuery(
            """
            select 
                n.*, 
                klikk.notifikasjonsid is not null as klikketPaa
            from notifikasjon as n
            left outer join brukerklikk as klikk on
                klikk.notifikasjonsid = n.id
                and klikk.fnr = ?
            where (
                mottaker ->> '@type' = 'naermesteLeder'
                and mottaker ->> 'naermesteLederFnr' = ?
            ) or (
                mottaker ->> '@type' = 'altinn'
                and mottaker @> ANY (ARRAY [$tilgangerJsonB]::jsonb[])
            )
            order by opprettet_tidspunkt desc
            limit 200
        """, {
                string(fnr)
                string(fnr)
            }) {
            when (val type = getString("type")) {
                "BESKJED" -> BrukerModel.Beskjed(
                    merkelapp = getString("merkelapp"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottaker = objectMapper.readValue(getString("mottaker")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    klikketPaa = getBoolean("klikketPaa")
                )
                "OPPGAVE" -> BrukerModel.Oppgave(
                    merkelapp = getString("merkelapp"),
                    tilstand = BrukerModel.Oppgave.Tilstand.valueOf(getString("tilstand")),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottaker = objectMapper.readValue(getString("mottaker")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    klikketPaa = getBoolean("klikketPaa")
                )
                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }

        notifikasjoner
            .filter { notifikasjon ->
                when (val mottaker = notifikasjon.mottaker) {
                    is NærmesteLederMottaker ->
                        ansatteLookupTable.contains(
                            NærmesteLederService.NærmesteLederFor(
                                ansattFnr = mottaker.ansattFnr,
                                virksomhetsnummer = mottaker.virksomhetsnummer
                            )
                        )
                    else -> true
                }
            }
    }

    override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String? =
        database.runNonTransactionalQuery(
            """
                SELECT virksomhetsnummer FROM notifikasjonsid_virksomhet_map WHERE notifikasjonsid = ? LIMIT 1
            """, {
                uuid(notifikasjonsid)
            }) {
            getString("virksomhetsnummer")!!
        }.getOrNull(0)

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        val ignored: Unit = when (hendelse) {
            is Hendelse.BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is Hendelse.BrukerKlikket -> oppdaterModellEtterBrukerKlikket(hendelse)
            is Hendelse.OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is Hendelse.OppgaveUtført -> oppdaterModellEtterOppgaveUtført(hendelse)
            is Hendelse.SoftDelete -> oppdaterModellEtterDelete(hendelse.id)
            is Hendelse.HardDelete -> oppdaterModellEtterDelete(hendelse.id)
        }
    }

    private suspend fun oppdaterModellEtterDelete(hendelsesId: UUID) {
        database.transaction({
            throw Error("Delete", it)
        }) {
            executeCommand(""" DELETE FROM notifikasjon WHERE id = ?;""") {
                uuid(hendelsesId)
            }

            executeCommand("""DELETE FROM notifikasjonsid_virksomhet_map WHERE notifikasjonsid = ?;""") {
                uuid(hendelsesId)
            }

            executeCommand("""DELETE FROM brukerklikk WHERE notifikasjonsid = ?;""") {
                uuid(hendelsesId)
            }
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtført(utførtHendelse: Hendelse.OppgaveUtført) {
        database.nonTransactionalCommand(
            """
            UPDATE notifikasjon
            SET tilstand = '${ProdusentModel.Oppgave.Tilstand.UTFOERT}'
            WHERE id = ?
        """
        ) {
            uuid(utførtHendelse.id)
        }
    }

    private suspend fun oppdaterModellEtterBrukerKlikket(brukerKlikket: Hendelse.BrukerKlikket) {
        database.nonTransactionalCommand(
            """
            INSERT INTO brukerklikk(fnr, notifikasjonsid) VALUES (?, ?)
            ON CONFLICT ON CONSTRAINT brukerklikk_pkey
            DO NOTHING
        """
        ) {
            string(brukerKlikket.fnr)
            uuid(brukerKlikket.notifikasjonsId)
        }
    }

    private suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: Hendelse.BeskjedOpprettet) {
        val rollbackHandler = { ex: Exception ->
            if (ex is PSQLException && PSQLState.UNIQUE_VIOLATION.state == ex.sqlState) {
                log.error("forsøk på å endre eksisterende beskjed")
            }
            /* TODO: ikke kast exception, hvis vi er sikker på at dette er en duplikat (at-least-once). */
            throw ex
        }

        val nyBeskjed = beskjedOpprettet.tilQueryDomene()

        database.transaction(rollbackHandler) {
            executeCommand(
                """
                insert into notifikasjon(
                    type,
                    tilstand,
                    id,
                    merkelapp,
                    tekst,
                    grupperingsid,
                    lenke,
                    ekstern_id,
                    opprettet_tidspunkt,
                    mottaker
                )
                values ('BESKJED', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json);
            """
            ) {
                uuid(nyBeskjed.id)
                string(nyBeskjed.merkelapp)
                string(nyBeskjed.tekst)
                nullableString(nyBeskjed.grupperingsid)
                string(nyBeskjed.lenke)
                string(nyBeskjed.eksternId)
                timestamptz(nyBeskjed.opprettetTidspunkt)
                string(objectMapper.writeValueAsString(nyBeskjed.mottaker))
            }

            executeCommand(
                """
                INSERT INTO notifikasjonsid_virksomhet_map(notifikasjonsid, virksomhetsnummer) VALUES (?, ?)
            """
            ) {
                uuid(beskjedOpprettet.id)
                string(beskjedOpprettet.virksomhetsnummer)
            }
        }
    }

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: Hendelse.OppgaveOpprettet) {
        val rollbackHandler = { ex: Exception ->
            if (ex is PSQLException && PSQLState.UNIQUE_VIOLATION.state == ex.sqlState) {
                log.error("forsøk på å endre eksisterende beskjed")
            }
            /* TODO: ikke kast exception, hvis vi er sikker på at dette er en duplikat (at-least-once). */
            throw ex
        }

        val nyBeskjed = oppgaveOpprettet.tilQueryDomene()

        database.transaction(rollbackHandler) {
            executeCommand(
                """
                insert into notifikasjon(
                    type,
                    tilstand,
                    id,
                    merkelapp,
                    tekst,
                    grupperingsid,
                    lenke,
                    ekstern_id,
                    opprettet_tidspunkt,
                    mottaker
                )
                values ('OPPGAVE', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json);
            """
            ) {
                uuid(nyBeskjed.id)
                string(nyBeskjed.merkelapp)
                string(nyBeskjed.tekst)
                nullableString(nyBeskjed.grupperingsid)
                string(nyBeskjed.lenke)
                string(nyBeskjed.eksternId)
                timestamptz(nyBeskjed.opprettetTidspunkt)
                string(objectMapper.writeValueAsString(nyBeskjed.mottaker))
            }

            executeCommand(
                """
                INSERT INTO notifikasjonsid_virksomhet_map(notifikasjonsid, virksomhetsnummer) VALUES (?, ?)
            """
            ) {
                uuid(oppgaveOpprettet.id)
                string(oppgaveOpprettet.virksomhetsnummer)
            }
        }
    }
}
