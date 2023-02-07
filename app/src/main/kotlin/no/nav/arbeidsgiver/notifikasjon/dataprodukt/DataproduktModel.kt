@file:JvmName("DataproduktKt")

package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnReporteeMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRolleMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.security.MessageDigest
import java.util.*

class DataproduktModel(
    val database: Database,
) {
    val log = logger()

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignore : Any = when (hendelse) {
            is BeskjedOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon 
                        (produsent_id, notifikasjon_id, notifikasjon_type, merkelapp, mottaker, checksum, opprettet_tidspunkt)
                    values (?, ?, 'beskjed', ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottakere.oppsummering())
                    string(hendelse.tekst.toHash())
                    timestamp_with_timezone(hendelse.opprettetTidspunkt)
                }

                oppdaterVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
                    merkelapp = hendelse.merkelapp,
                    iterable = hendelse.eksterneVarsler
                )
            }
            is OppgaveOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon(
                        produsent_id, 
                        notifikasjon_id, 
                        notifikasjon_type, 
                        merkelapp, 
                        mottaker, 
                        checksum, 
                        opprettet_tidspunkt,
                        frist
                    )
                    values
                    (?, ?, 'oppgave', ?, ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottakere.oppsummering())
                    string(hendelse.tekst.toHash())
                    timestamp_without_timezone_utc(hendelse.opprettetTidspunkt)
                    nullableDate(hendelse.frist)
                }

                oppdaterVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
                    merkelapp = hendelse.merkelapp,
                    iterable = hendelse.eksterneVarsler
                )
            }
            is OppgaveUtført -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    update notifikasjon 
                        set utfoert_tidspunkt = ?,
                            utgaatt_tidspunkt = null
                        where notifikasjon_id = ?
                    """
                ) {
                    timestamp_without_timezone_utc(metadata.timestamp)
                    uuid(hendelse.notifikasjonId)
                }
            }
            is OppgaveUtgått -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    update notifikasjon 
                        set utgaatt_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    timestamp_without_timezone_utc(metadata.timestamp)
                    uuid(hendelse.notifikasjonId)
                }
            }
            is BrukerKlikket -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon_klikk 
                        (hendelse_id, notifikasjon_id, klikket_paa_tidspunkt)
                    values (?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    timestamp_without_timezone_utc(metadata.timestamp)
                }
            }
            is EksterntVarselVellykket -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into varsel_resultat 
                        (hendelse_id, varsel_id, notifikasjon_id, produsent_id, status)
                    values
                    (?, ?, ?, ?, 'vellykket')
                    on conflict do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.varselId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.produsentId)
                }
            }
            is EksterntVarselFeilet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into varsel_resultat 
                        (hendelse_id, varsel_id, notifikasjon_id, produsent_id, status, feilkode)
                    values
                        (?, ?, ?, ?, 'feilet', ?)
                    on conflict do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.varselId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.produsentId)
                    string(hendelse.altinnFeilkode)
                }
            }
            is SoftDelete -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    update notifikasjon 
                        set soft_deleted_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    timestamp_without_timezone_utc(hendelse.deletedAt)
                    uuid(hendelse.aggregateId)
                }
                database.nonTransactionalExecuteUpdate(
                    """
                    update sak 
                        set soft_deleted_tidspunkt = ?
                        where sak_id = ?
                    """
                ) {
                    timestamp_without_timezone_utc(hendelse.deletedAt)
                    uuid(hendelse.aggregateId)
                }
            }
            is HardDelete -> {
                // noop
            }

            is SakOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into sak 
                        (produsent_id, sak_id, merkelapp, mottaker, opprettet_tidspunkt)
                    values (?, ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.sakId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottakere.oppsummering())
                    timestamp_with_timezone(hendelse.oppgittTidspunkt ?: hendelse.mottattTidspunkt)
                }
            }
            is NyStatusSak -> {
                // noop
            }
            is PåminnelseOpprettet -> {
                // noop
            }
        }
    }

    private suspend fun oppdaterVarselBestilling(
        notifikasjonId: UUID,
        produsentId: String,
        merkelapp: String,
        iterable: List<EksterntVarsel>,
    ) {
        database.nonTransactionalExecuteBatch(
            """
            insert into varsel_bestilling 
                (varsel_id, varsel_type, notifikasjon_id, produsent_id, merkelapp, mottaker, checksum)
            values
                (?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing;
            """,
            iterable
        ) { eksterntVarsel ->
            when (eksterntVarsel) {
                is EpostVarselKontaktinfo -> {
                    uuid(eksterntVarsel.varselId)
                    string("epost_kontaktinfo")
                    uuid(notifikasjonId)
                    string(produsentId)
                    string(merkelapp)
                    string(eksterntVarsel.epostAddr)
                    string((eksterntVarsel.tittel + eksterntVarsel.htmlBody).toHash())
                }
                is SmsVarselKontaktinfo -> {
                    uuid(eksterntVarsel.varselId)
                    string("sms_kontaktinfo")
                    uuid(notifikasjonId)
                    string(produsentId)
                    string(merkelapp)
                    string(eksterntVarsel.tlfnr)
                    string(eksterntVarsel.smsTekst.toHash())
                }
            }
        }
    }
}

fun List<Mottaker>.oppsummering(): String =
    map {
        when (it) {
            is NærmesteLederMottaker -> "NærmesteLeder"
            is AltinnMottaker -> "Altinn:${it.serviceCode}:${it.serviceEdition}"
            is AltinnReporteeMottaker -> "AltinnReporteeMottaker"
            is AltinnRolleMottaker -> "AltinnRolle:${it.roleDefinitionCode}"
        }
    }
        .sorted()
        .joinToString(",")


fun String.toHash(alg: String = "MD5"): String {
    return MessageDigest
        .getInstance(alg)
        .digest(toByteArray())
        .fold("") { acc, it -> acc + "%02x".format(it) }
}