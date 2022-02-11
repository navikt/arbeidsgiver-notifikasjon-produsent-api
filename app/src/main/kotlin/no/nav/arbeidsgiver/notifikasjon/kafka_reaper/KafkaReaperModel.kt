package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.time.OffsetDateTime
import java.util.*

interface KafkaReaperModel {
    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse)
    suspend fun erSlettet(notifikasjonId: UUID): Boolean
    suspend fun alleRelaterteHendelser(notifikasjonId: UUID): List<UUID>
    suspend fun fjernRelasjon(hendelseId: UUID)
}

class KafkaReaperModelImpl(
    val database: Database
) : KafkaReaperModel {
    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        database.transaction({}) {
            executeUpdate("""
                INSERT INTO notifikasjon_hendelse_relasjon
                (
                    hendelse_id,
                    notifikasjon_id,
                    hendelse_type
                ) 
                VALUES 
                (
                    ?,
                    ?,
                    ?
                )
                ON CONFLICT DO NOTHING
            """
            ) {
                uuid(hendelse.hendelseId)
                uuid(hendelse.aggregateId)
                string(hendelse.typeNavn)
            }

            if (hendelse is Hendelse.HardDelete) {
                executeUpdate(
                    """
                        INSERT INTO deleted_notifikasjon (notifikasjon_id, deleted_at) 
                        VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                    """) {
                        uuid(hendelse.aggregateId)
                        timestamptz(OffsetDateTime.now())
                }
            }
        }
    }

    override suspend fun alleRelaterteHendelser(notifikasjonId: UUID): List<UUID> {
        return database.nonTransactionalExecuteQuery(
            """
                SELECT hendelse_id FROM notifikasjon_hendelse_relasjon
                WHERE notifikasjon_id = ?
            """,
            {
                uuid(notifikasjonId)
            }
        ) {
            getObject("hendelse_id", UUID::class.java)
        }
    }

    override suspend fun erSlettet(notifikasjonId: UUID): Boolean {
        return database.nonTransactionalExecuteQuery(
            """
                SELECT *
                FROM deleted_notifikasjon
                WHERE notifikasjon_id = ?
            """,
            {
                uuid(notifikasjonId)
            }
        ) {
        }
            .isNotEmpty()
    }

    override suspend fun fjernRelasjon(hendelseId: UUID) {
        database.nonTransactionalExecuteUpdate(
            """
                DELETE FROM notifikasjon_hendelse_relasjon
                WHERE hendelse_id = ?
            """
        ) {
            uuid(hendelseId)
        }
    }
}

val Hendelse.typeNavn: String get() = when (this) {
    is Hendelse.SakOpprettet -> TODO()
    is Hendelse.NyStatusSak -> TODO()
    is Hendelse.SoftDelete -> "SoftDelete"
    is Hendelse.HardDelete -> "HardDelete"
    is Hendelse.OppgaveUtført -> "OppgaveUtført"
    is Hendelse.BrukerKlikket -> "BrukerKlikket"
    is Hendelse.BeskjedOpprettet -> "BeskjedOpprettet"
    is Hendelse.OppgaveOpprettet -> "OppgaveOpprettet"
    is Hendelse.EksterntVarselVellykket -> "EksterntVarselVellykket"
    is Hendelse.EksterntVarselFeilet -> "EksterntVarselFeilet"
}
