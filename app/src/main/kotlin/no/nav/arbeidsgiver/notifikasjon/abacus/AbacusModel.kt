package no.nav.arbeidsgiver.notifikasjon.abacus

import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.security.MessageDigest
import java.time.OffsetDateTime

interface AbacusModel {
    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse)
}

class AbacusModelImpl(
    val database: Database,
) : AbacusModel {
    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
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
                    timestamptz(hendelse.opprettetTidspunkt)
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
                    timestamptz(OffsetDateTime.now()) // TODO: propager fra header
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
                    timestamptz(OffsetDateTime.now()) // TODO: propager fra header
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
                    timestamptz(OffsetDateTime.now()) // TODO: propager fra header
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