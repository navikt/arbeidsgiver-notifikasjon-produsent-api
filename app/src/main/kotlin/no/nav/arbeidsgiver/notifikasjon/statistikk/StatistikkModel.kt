package no.nav.arbeidsgiver.notifikasjon.statistikk

import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.security.MessageDigest

interface StatistikkModel {
    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata)
}

class StatistikkModelImpl(
    val database: Database,
) : StatistikkModel {
    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        when (hendelse) {
            is Hendelse.BeskjedOpprettet -> {
                database.nonTransactionalCommand(
                    """
                    insert into notifikasjon_statistikk 
                        (notifikasjon_id, hendelse_type, merkelapp, mottaker, checksum, opprettet_tidspunkt)
                    values
                        (?, ?, ?, ?, ?, ?)
                    """
                ) {
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.typeNavn)
                    string(hendelse.merkelapp)
                    string(hendelse.mottaker.typeNavn)
                    string(hendelse.tekst.toHash())
                    timestamptz(hendelse.opprettetTidspunkt)
                }
            }
            is Hendelse.OppgaveOpprettet -> {
                database.nonTransactionalCommand(
                    """
                    insert into notifikasjon_statistikk 
                        (notifikasjon_id, hendelse_type, merkelapp, mottaker, checksum, opprettet_tidspunkt)
                    values
                    (?, ?, ?, ?, ?, ?)
                    """
                ) {
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.typeNavn)
                    string(hendelse.merkelapp)
                    string(hendelse.mottaker.typeNavn)
                    string(hendelse.tekst.toHash())
                    timestamp_utc(hendelse.opprettetTidspunkt)
                }
            }
            is Hendelse.OppgaveUtført -> {
                database.nonTransactionalCommand(
                    """
                    update notifikasjon_statistikk 
                        set utfoert_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    timestamp_utc(metadata.timestamp)
                    uuid(hendelse.notifikasjonId)
                }
            }
            is Hendelse.BrukerKlikket -> {
                database.nonTransactionalCommand(
                    """
                    insert into notifikasjon_statistikk_klikk 
                        (hendelse_id, notifikasjon_id, klikket_paa_tidspunkt)
                    values
                    (?, ?, ?)
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    timestamp_utc(metadata.timestamp)
                }
            }
            is Hendelse.SoftDelete -> {
                database.nonTransactionalCommand(
                    """
                    update notifikasjon_statistikk 
                        set soft_deleted_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    timestamp_utc(hendelse.deletedAt)
                    uuid(hendelse.notifikasjonId)
                }
            }
            is Hendelse.HardDelete -> {
                // noop
            }
        }
    }
}

val Hendelse.typeNavn: String
    get() = when (this) {
        is Hendelse.SoftDelete -> "SoftDelete"
        is Hendelse.HardDelete -> "HardDelete"
        is Hendelse.OppgaveUtført -> "OppgaveUtført"
        is Hendelse.BrukerKlikket -> "BrukerKlikket"
        is Hendelse.BeskjedOpprettet -> "BeskjedOpprettet"
        is Hendelse.OppgaveOpprettet -> "OppgaveOpprettet"
    }

val Mottaker.typeNavn: String
    get() = when (this) {
        is NærmesteLederMottaker -> "NærmesteLeder"
        is AltinnMottaker -> "Altinn:${serviceCode}:${serviceEdition}"
    }


fun String.toHash(alg: String = "MD5"): String {
    return MessageDigest
        .getInstance(alg)
        .digest(toByteArray())
        .fold("") { acc, it -> acc + "%02x".format(it) }
}