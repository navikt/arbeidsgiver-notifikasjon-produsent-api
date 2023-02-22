@file:JvmName("DataproduktKt")

package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
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
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseTidspunkt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.ScheduledTime
import java.security.MessageDigest
import java.time.Instant
import java.util.*

class DataproduktModel(
    val database: Database,
) {
    val log = logger()

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        database.nonTransactionalExecuteUpdate("""
            insert into aggregat_hendelse(
                hendelse_id,
                hendelse_type,
                aggregat_id,
                kilde_app_navn,
                virksomhetsnummer,
                produsent_id,
                kafka_timestamp 
            ) values (?, ?, ?, ?, ? ,?, ?)
            on conflict do nothing
        """) {
            with(hendelse) {
                uuid(hendelseId)
                text(hendelse::class.simpleName!!)
                uuid(aggregateId)
                text(kildeAppNavn)
                text(virksomhetsnummer)
                nullableText(produsentId)
                instantAsText(metadata.timestamp)
            }
        }

        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignore : Any = when (hendelse) {
            is BeskjedOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon 
                    (
                        notifikasjon_id,
                        notifikasjon_type,
                        produsent_id,
                        merkelapp,
                        ekstern_id,
                        tekst,
                        grupperingsid,
                        lenke,
                        opprettet_tidspunkt
                    )
                    values (?, 'BESKJED', ?, ?, ?, ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    with(hendelse) {
                        uuid(notifikasjonId)
                        text(produsentId)
                        text(merkelapp)
                        text(eksternId)
                        text(tekst)
                        nullableText(grupperingsid)
                        text(lenke)
                        toInstantAsText(opprettetTidspunkt)
                    }
                }

                storeMottakere(
                    notifikasjonId = hendelse.notifikasjonId,
                    sakId = null,
                    mottakere = hendelse.mottakere,
                )

                if (hendelse.hardDelete != null) {
                    with(hendelse) {
                        storeHardDelete(
                            aggregatId = aggregateId,
                            bestillingHendelsesid = hendelseId,
                            bestillingType = "OPPRETTELSE",
                            spesifikasjon = hendelse.hardDelete,
                            utregnetTidspunkt = ScheduledTime(hendelse.hardDelete, metadata.timestamp).happensAt(),
                        )
                    }
                }

                opprettVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
                    merkelapp = hendelse.merkelapp,
                    eksterneVarsler = hendelse.eksterneVarsler,
                    opprinnelse = "BeskjedOpprettet.eksterneVarsler",
                    statusUtsending = "UTSENDING_BESTILT",
                )
            }
            is OppgaveOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon 
                    (
                        notifikasjon_id,
                        notifikasjon_type,
                        produsent_id,
                        merkelapp,
                        ekstern_id,
                        tekst,
                        grupperingsid,
                        lenke,
                        opprettet_tidspunkt,
                        frist,
                        paaminnelse_bestilling_spesifikasjon_type,
                        paaminnelse_bestilling_spesifikasjon_tid,
                        paaminnelse_bestilling_utregnet_tid
                    )
                    values (?, 'OPPGAVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    with(hendelse) {
                        uuid(notifikasjonId)
                        text(produsentId)
                        text(merkelapp)
                        text(eksternId)
                        text(tekst)
                        nullableText(grupperingsid)
                        text(lenke)
                        toInstantAsText(opprettetTidspunkt)
                        nullableDate(frist)
                        when (val tidspunkt = påminnelse?.tidspunkt) {
                            is PåminnelseTidspunkt.Konkret -> {
                                text("Konkret")
                                localDateTimeAsText(tidspunkt.konkret)
                            }
                            is PåminnelseTidspunkt.EtterOpprettelse -> {
                                text("EtterOpprettelse")
                                periodAsText(tidspunkt.etterOpprettelse)
                            }
                            is PåminnelseTidspunkt.FørFrist -> {
                                text("FørFrist")
                                periodAsText(tidspunkt.førFrist)
                            }
                            null -> {
                                nullableText(null)
                                nullableText(null)
                            }
                        }
                        nullableInstantAsText(påminnelse?.tidspunkt?.påminnelseTidspunkt)
                    }
                }

                storeMottakere(
                    notifikasjonId = hendelse.notifikasjonId,
                    sakId = null,
                    mottakere = hendelse.mottakere,
                )

                with(hendelse) {
                    if (hardDelete != null) {
                        storeHardDelete(
                            aggregatId = aggregateId,
                            bestillingHendelsesid = hendelseId,
                            bestillingType = "OPPRETTELSE",
                            spesifikasjon = hardDelete,
                            utregnetTidspunkt = ScheduledTime(hardDelete, metadata.timestamp).happensAt(),
                        )
                    }
                }

                if (hendelse.påminnelse != null) {
                    opprettVarselBestilling(
                        notifikasjonId = hendelse.notifikasjonId,
                        produsentId = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        eksterneVarsler = hendelse.påminnelse.eksterneVarsler,
                        opprinnelse = "OppgaveOpprettet.påminnelse",
                        statusUtsending = "UTSENDING_IKKE_AVGJORT",
                    )
                }

                opprettVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
                    merkelapp = hendelse.merkelapp,
                    eksterneVarsler = hendelse.eksterneVarsler,
                    opprinnelse = "OppgaveOpprettet.eksterneVarsler",
                    statusUtsending = "UTSENDING_BESTILT",
                )
            }
            is OppgaveUtført -> {
                with(hendelse) {
                    if (hardDelete != null) {
                        storeHardDelete(
                            aggregatId = aggregateId,
                            bestillingHendelsesid = hendelseId,
                            bestillingType = "STATUSENDRING",
                            strategi = hardDelete.strategi.toString(),
                            spesifikasjon = hardDelete.nyTid,
                            utregnetTidspunkt = ScheduledTime(hardDelete.nyTid, metadata.timestamp).happensAt(),
                        )
                    }
                }

                database.nonTransactionalExecuteUpdate(
                    """
                       update notifikasjon 
                       set utfoert_tidspunkt = ?
                       where notifikasjon_id = ?
                    """
                ) {
                    instantAsText(metadata.timestamp)
                    uuid(hendelse.notifikasjonId)
                }
            }
            is OppgaveUtgått -> {
                with(hendelse) {
                    if (hardDelete != null) {
                        storeHardDelete(
                            aggregatId = aggregateId,
                            bestillingHendelsesid = hendelseId,
                            bestillingType = "STATUSENDRING",
                            strategi = hardDelete.strategi.toString(),
                            spesifikasjon = hardDelete.nyTid,
                            utregnetTidspunkt = ScheduledTime(hardDelete.nyTid, utgaattTidspunkt.toInstant()).happensAt(),
                        )
                    }
                }

                database.nonTransactionalExecuteUpdate(
                    """
                        update notifikasjon 
                        set utgaatt_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    instantAsText(metadata.timestamp)
                    uuid(hendelse.notifikasjonId)
                }

            }
            is PåminnelseOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                        update notifikasjon 
                        set paaminnelse_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    instantAsText(hendelse.opprettetTidpunkt)
                    uuid(hendelse.notifikasjonId)
                }

                database.nonTransactionalExecuteBatch(
                    """
                       update ekstern_varsel
                       set status_utsending = 'UTSENDING_BESTILT'
                       where varsel_id = ?
                    """,
                    hendelse.eksterneVarsler
                ) {
                    uuid(it.varselId)
                }
            }
            is BrukerKlikket -> {
                database.nonTransactionalExecuteUpdate(
                    """
                        insert into notifikasjon_klikk (hendelse_id, notifikasjon_id, fnr, klikket_paa_tidspunkt) 
                        values (?, ?, ?, ?)
                        on conflict do nothing
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    text(hendelse.fnr)
                    instantAsText(metadata.timestamp)
                }

            }
            is EksterntVarselVellykket -> {

            }
            is EksterntVarselFeilet -> {

            }
            is SoftDelete -> {

            }
            is HardDelete -> {

            }

            is SakOpprettet -> {

            }
            is NyStatusSak -> {

            }

        }
    }

    private suspend fun storeHardDelete(
        aggregatId: UUID,
        bestillingType: String,
        bestillingHendelsesid: UUID,
        strategi: String? = null,
        spesifikasjon: HendelseModel.LocalDateTimeOrDuration,
        utregnetTidspunkt: Instant,
    ) {
        database.nonTransactionalExecuteUpdate("""
            insert into hard_delete_bestilling
            (
                aggregat_id,
                bestilling_type,
                bestilling_hendelsesid,
                strategi,
                spesifikasjon,
                utregnet_tidspunkt
            )
            values (?, ?, ?, ?, ?, ?)
            on conflict do nothing
        """) {
            uuid(aggregatId)
            text(bestillingType)
            uuid(bestillingHendelsesid)
            nullableText(strategi)
            text(spesifikasjon.toString())
            instantAsText(utregnetTidspunkt)
        }
    }

    private suspend fun storeMottakere(notifikasjonId: UUID?, sakId: UUID?, mottakere: List<Mottaker>) {
        database.nonTransactionalExecuteBatch("""
            insert into mottaker_naermeste_leder (sak_id, notifikasjon_id, virksomhetsnummer, fnr_leder, fnr_ansatt)
            values (?, ?, ?, ?, ?)
            on conflict do nothing
        """,
            mottakere.filterIsInstance<NærmesteLederMottaker>()
        ) {
            nullableUuid(sakId)
            nullableUuid(notifikasjonId)
            text(it.virksomhetsnummer)
            text(it.naermesteLederFnr)
            text(it.ansattFnr)
        }

        database.nonTransactionalExecuteBatch("""
            insert into mottaker_enkeltrettighet (sak_id, notifikasjon_id, virksomhetsnummer, service_code, service_edition)
            values (?, ?, ?, ?, ?)
            on conflict do nothing
        """,
            mottakere.filterIsInstance<AltinnMottaker>()
        ) {
            nullableUuid(sakId)
            nullableUuid(notifikasjonId)
            text(it.virksomhetsnummer)
            text(it.serviceCode)
            text(it.serviceEdition)
        }
    }

    private suspend fun opprettVarselBestilling(
        notifikasjonId: UUID,
        produsentId: String,
        merkelapp: String,
        opprinnelse: String,
        eksterneVarsler: List<EksterntVarsel>,
        statusUtsending: String,
    ) {
        database.nonTransactionalExecuteBatch(
            """
            insert into ekstern_varsel
            (
                varsel_id,
                varsel_type,
                notifikasjon_id,
                merkelapp,
                sendevindu,
                sendetidspunkt,
                produsent_id,
                sms_tekst,
                html_tittel,
                html_body,
                opprinnelse,
                status_utsending
            )
            values
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing;
            """,
            eksterneVarsler
        ) { eksterntVarsel ->
            when (eksterntVarsel) {
                is EpostVarselKontaktinfo -> {
                    with(eksterntVarsel) {
                        uuid(varselId)
                        text("EPOST") //varsel_type
                        uuid(notifikasjonId)
                        text(merkelapp)
                        enumAsText(sendevindu)
                        nullableLocalDateTimeAsText(sendeTidspunkt)
                        text(produsentId)
                        nullableText(null)
                        text(tittel)
                        text(htmlBody)
                        text(opprinnelse)
                        text(statusUtsending)
                    }
                }
                is SmsVarselKontaktinfo -> {
                    with(eksterntVarsel) {
                        uuid(varselId)
                        text("SMS") //varsel_type
                        uuid(notifikasjonId)
                        text(merkelapp)
                        enumAsText(sendevindu)
                        nullableLocalDateTimeAsText(sendeTidspunkt)
                        text(produsentId)
                        text(smsTekst)
                        nullableText(null)
                        nullableText(null)
                        text(opprinnelse)
                        text(statusUtsending)
                    }
                }
            }
        }

        database.nonTransactionalExecuteBatch("""
            insert into ekstern_varsel_mottaker_tlf(varsel_id, tlf) 
            values (?, ?)
            on conflict do nothing
        """,
            eksterneVarsler.filterIsInstance<SmsVarselKontaktinfo>()
        ) {
            uuid(it.varselId)
            text(it.tlfnr)
        }

        database.nonTransactionalExecuteBatch("""
            insert into ekstern_varsel_mottaker_epost (varsel_id, epost) 
            values (?, ?)
            on conflict do nothing
        """,
            eksterneVarsler.filterIsInstance<EpostVarselKontaktinfo>()
        ) {
            uuid(it.varselId)
            text(it.epostAddr)
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