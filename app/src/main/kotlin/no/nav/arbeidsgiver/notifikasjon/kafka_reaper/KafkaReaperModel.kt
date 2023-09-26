package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
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
                text(hendelse.typeNavn)
            }

            if (hendelse is HardDelete) {
                executeUpdate(
                    """
                        INSERT INTO deleted_notifikasjon (notifikasjon_id, deleted_at) 
                        VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                    """) {
                        uuid(hendelse.aggregateId)
                        timestamp_with_timezone(OffsetDateTime.now())
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
                delete from notifikasjon_hendelse_relasjon
                where hendelse_id = ? 
                and hendelse_type != 'HardDelete'
            """
        ) {
            uuid(hendelseId)
        }
    }
}

val Hendelse.typeNavn: String get() = when (this) {
    is SakOpprettet -> "SakOpprettet"
    is NyStatusSak -> "NyStatusSak"
    is SoftDelete -> "SoftDelete"
    is HardDelete -> "HardDelete"
    is OppgaveUtført -> "OppgaveUtført"
    is OppgaveUtgått -> "OppgaveUtgått"
    is BrukerKlikket -> "BrukerKlikket"
    is BeskjedOpprettet -> "BeskjedOpprettet"
    is OppgaveOpprettet -> "OppgaveOpprettet"
    is EksterntVarselVellykket -> "EksterntVarselVellykket"
    is EksterntVarselFeilet -> "EksterntVarselFeilet"
    is PåminnelseOpprettet -> "PåminnelseOpprettet"
    is FristUtsatt -> "FristUtsatt"
}
