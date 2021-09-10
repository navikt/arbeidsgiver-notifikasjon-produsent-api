package no.nav.arbeidsgiver.notifikasjon.abacus

import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.security.MessageDigest

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
                    (hendelseId, hendelseType, merkelapp, mottaker, checksum, tidspunkt)
                values
                    (?, ?, ?, ?, ?, ?)
                    """
                ) {
                    uuid(hendelse.hendelseId)
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
                    (hendelseId, hendelseType, merkelapp, mottaker, checksum, created)
                values
                    (?, ?, ?, ?, ?, ?)
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    string(hendelse.typeNavn)
                    string(hendelse.merkelapp)
                    string(hendelse.mottaker.typeNavn)
                    string(hendelse.tekst.toHash())
                    timestamptz(hendelse.opprettetTidspunkt)
                }
            }
            is Hendelse.OppgaveUtført -> {
                // TODO:
                //  hent eksisterende data fra modell
                //  skriv ny rad med riktig type og sett oppdatert tidspunkt til "now"
            }
            is Hendelse.BrukerKlikket -> {
                // noop
            }
            is Hendelse.SoftDelete -> {
                //
            }
            is Hendelse.HardDelete -> {
                // hva gjør vi her, skal vi slette alle stats relatert hendelsen?
                // bør ikke være noe identifiserende i denne modulen/appen
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


fun String.toHash(alg: String = "SHA1"): String {
    return MessageDigest
        .getInstance(alg)
        .digest(toByteArray())
        .fold("") { acc, it -> acc + "%02x".format(it) }
}