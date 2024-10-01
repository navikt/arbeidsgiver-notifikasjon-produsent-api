@file:JvmName("DataproduktKt")

package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import no.nav.arbeidsgiver.notifikasjon.hendelse.HardDeletedRepository
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NesteStegSak
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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ParameterSetters
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.ScheduledTime
import java.time.Instant
import java.util.*

class DataproduktModel(
    val database: Database,
) : HardDeletedRepository(database) {
    val log = logger()

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        if (erHardDeleted(hendelse.aggregateId)) {
            log.info("skipping harddeleted event {}", hendelse)
            return
        }
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

        when (hendelse) {
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
                        ny_lenke,
                        opprettet_tidspunkt
                    )
                    values (?, 'BESKJED', ?, ?, ?, ?, ?, ?, ?, ?)
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
                        nullableText(null)
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
                        ny_lenke,
                        opprettet_tidspunkt,
                        frist,
                        paaminnelse_bestilling_spesifikasjon_type,
                        paaminnelse_bestilling_spesifikasjon_tid,
                        paaminnelse_bestilling_utregnet_tid
                    )
                    values (?, 'OPPGAVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        nullableText(null)
                        toInstantAsText(opprettetTidspunkt)
                        nullableDate(frist)
                        setPåminnelseFelter(this.påminnelse)
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

                markerIngenUtsendingPåPåminnelseEksterneVarsler(hendelse.notifikasjonId)

                database.nonTransactionalExecuteUpdate(
                    """
                        update notifikasjon
                        set utfoert_tidspunkt = ?,
                            ny_lenke = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    instantAsText(metadata.timestamp)
                    nullableText(hendelse.nyLenke)
                    uuid(hendelse.notifikasjonId)
                }
            }
            is OppgaveUtgått -> {
                markerIngenUtsendingPåPåminnelseEksterneVarsler(hendelse.notifikasjonId)

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
                        set utgaatt_tidspunkt = ?, 
                            ny_lenke = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    instantAsText(metadata.timestamp)
                    nullableText(hendelse.nyLenke)
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

                updateEksternVarsel(hendelse.eksterneVarsler.map { it.varselId }, "UTSENDING_BESTILT")
            }

            is BrukerKlikket -> {
                database.nonTransactionalExecuteUpdate(
                    """
                        insert into notifikasjon_klikk (hendelse_id, notifikasjon_id, fnr, klikket_paa_tidspunkt) 
                        select val.hendelse_id, val.notifikasjon_id, val.fnr, val.klikket_paa_tidspunkt
                        from  (
                          values (?, ?, ?, ?)
                        ) val (hendelse_id, notifikasjon_id, fnr, klikket_paa_tidspunkt)
                        join  notifikasjon using (notifikasjon_id)
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
                updateEksternVarsel(listOf(hendelse.varselId), "UTSENDING_VELLYKKET", null, metadata.timestamp)
                database.nonTransactionalExecuteBatch(
                    """
                           insert into ekstern_varsel_resultat (varsel_id, resultat_name, resultat_receiver, resultat_type)
                           values (?, ?, ?, ?)
                           on conflict do nothing 
                        """,
                    hendelse.råRespons.at("/notificationResult/0/endPoints/value/endPointResult").elements()
                        .asSequence()
                        .map {
                            mapOf(
                                "resultat_name" to it.at("/name/value").asText(),
                                "resultat_receiver" to it.at("/receiverAddress/value").asText(),
                                "resultat_type" to it.at("/transportType").asText(),
                            )
                        }.toList()
                ) {
                    uuid(hendelse.varselId)
                    text(it["resultat_name"]!!)
                    text(it["resultat_receiver"]!!)
                    text(it["resultat_type"]!!)
                }
            }
            is EksterntVarselFeilet -> {
                updateEksternVarsel(listOf(hendelse.varselId), "UTSENDING_FEILET", hendelse.altinnFeilkode,  metadata.timestamp)
            }
            is HendelseModel.EksterntVarselKansellert -> {
                updateEksternVarsel(listOf(hendelse.varselId), "UTSENDING_KANSELLERT", null,  metadata.timestamp)
            }

            is SoftDelete -> {
                if (hendelse.grupperingsid != null && hendelse.merkelapp != null){
                    database.nonTransactionalExecuteUpdate("""
                        update notifikasjon
                        set soft_deleted_tidspunkt = ?
                        where grupperingsid = ?
                        and merkelapp = ?
                    """) {
                        instantAsText(metadata.timestamp)
                        text(hendelse.grupperingsid)
                        text(hendelse.merkelapp)
                    }
                }
                database.nonTransactionalExecuteUpdate("""
                    update notifikasjon
                    set soft_deleted_tidspunkt = ?
                    where notifikasjon_id = ?
                """) {
                    instantAsText(metadata.timestamp)
                    uuid(hendelse.aggregateId)
                }
                database.nonTransactionalExecuteUpdate("""
                    update sak 
                    set soft_deleted_tidspunkt = ?
                    where sak_id = ?
                """) {
                    instantAsText(metadata.timestamp)
                    uuid(hendelse.aggregateId)
                }
            }

            is HardDelete -> {
                database.transaction {
                    if (hendelse.grupperingsid != null && hendelse.merkelapp != null){
                        executeUpdate("""
                            delete from notifikasjon
                            where grupperingsid = ?
                            and merkelapp = ?
                        """) {
                            text(hendelse.grupperingsid)
                            text(hendelse.merkelapp)
                        }
                    }
                    executeUpdate("""
                        delete from notifikasjon
                        where notifikasjon_id = ?
                    """) {
                        uuid(hendelse.aggregateId)
                    }
                    executeUpdate("""
                        delete from sak 
                        where sak_id = ?
                    """) {
                        uuid(hendelse.aggregateId)
                    }
                    executeUpdate("""
                        delete from aggregat_hendelse
                        where aggregat_id = ?
                    """) {
                        uuid(hendelse.aggregateId)
                    }
                    registrerHardDelete(this, hendelse)
                }
            }

            is SakOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                        insert into sak (
                            sak_id, grupperings_id, produsent_id, merkelapp, tittel, tilleggsinformasjon,  lenke, oppgitt_tidspunkt, mottatt_tidspunkt, soft_deleted_tidspunkt
                        ) 
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict do nothing
                    """
                ) {
                    uuid(hendelse.sakId)
                    nullableText(hendelse.grupperingsid)
                    text(hendelse.produsentId)
                    text(hendelse.merkelapp)
                    text(hendelse.tittel)
                    nullableText(hendelse.tilleggsinformasjon)
                    nullableText(hendelse.lenke)
                    nullableInstantAsText(hendelse.oppgittTidspunkt?.toInstant())
                    instantAsText(hendelse.mottattTidspunkt?.toInstant() ?: metadata.timestamp)
                    nullableInstantAsText(null)
                }

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
            }
            is NyStatusSak -> {
                database.nonTransactionalExecuteUpdate(
                    """
                        insert into sak_status (
                            status_id, idempotens_key, sak_id, status, overstyr_statustekst_med, oppgitt_tidspunkt, mottatt_tidspunkt, ny_lenke_til_sak
                        ) 
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict do nothing
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    text(hendelse.idempotensKey)
                    uuid(hendelse.sakId)
                    text(hendelse.status.toString())
                    nullableText(hendelse.overstyrStatustekstMed)
                    nullableInstantAsText(hendelse.oppgittTidspunkt?.toInstant())
                    instantAsText(hendelse.mottattTidspunkt.toInstant())
                    nullableText(hendelse.nyLenkeTilSak)
                }

                with(hendelse) {
                    if (hardDelete != null) {
                        storeHardDelete(
                            aggregatId = aggregateId,
                            bestillingHendelsesid = hendelseId,
                            bestillingType = "STATUSENDRING",
                            strategi = hardDelete.strategi.toString(),
                            spesifikasjon = hardDelete.nyTid,
                            utregnetTidspunkt = ScheduledTime(hardDelete.nyTid, opprettetTidspunkt.toInstant()).happensAt(),
                        )
                    }
                }
            }
            is FristUtsatt -> {
                database.nonTransactionalExecuteUpdate("""
                    update notifikasjon
                    set frist = ?,
                        paaminnelse_bestilling_spesifikasjon_type = ?,
                        paaminnelse_bestilling_spesifikasjon_tid = ?,
                        paaminnelse_bestilling_utregnet_tid = ?
                    where notifikasjon_id = ?
                """) {
                    date(hendelse.frist)
                    setPåminnelseFelter(hendelse.påminnelse)
                    uuid(hendelse.notifikasjonId)
                }
            }

            is NesteStegSak -> {
                database.nonTransactionalExecuteUpdate("""
                    update sak
                    set neste_steg = ?
                    where sak_id = ?
                """.trimIndent()) {
                    nullableText(hendelse.nesteSteg)
                    uuid(hendelse.sakId)
                }
            }

            is HendelseModel.KalenderavtaleOpprettet -> {
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
                        ny_lenke,
                        opprettet_tidspunkt
                    )
                    values (?, 'KALENDERAVTALE', ?, ?, ?, ?, ?, ?, ?, ?)
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
                        nullableText(null)
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

                if (hendelse.påminnelse != null) {
                    opprettVarselBestilling(
                        notifikasjonId = hendelse.notifikasjonId,
                        produsentId = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        eksterneVarsler = hendelse.påminnelse.eksterneVarsler,
                        opprinnelse = "KalenderavtaleOpprettet.påminnelse",
                        statusUtsending = "UTSENDING_IKKE_AVGJORT",
                    )
                }

                opprettVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
                    merkelapp = hendelse.merkelapp,
                    eksterneVarsler = hendelse.eksterneVarsler,
                    opprinnelse = "KalenderavtaleOpprettet.eksterneVarsler",
                    statusUtsending = "UTSENDING_BESTILT",
                )
            }

            is HendelseModel.KalenderavtaleOppdatert -> {
                with(hendelse) {
                    if (hardDelete != null) {
                        storeHardDelete(
                            aggregatId = aggregateId,
                            bestillingHendelsesid = hendelseId,
                            bestillingType = "OPPDATERING",
                            strategi = hardDelete.strategi.toString(),
                            spesifikasjon = hardDelete.nyTid,
                            utregnetTidspunkt = ScheduledTime(hardDelete.nyTid, metadata.timestamp).happensAt(),
                        )
                    }
                }

                database.nonTransactionalExecuteUpdate(
                    """
                        update notifikasjon
                        set ny_lenke = ?,
                        tekst = coalesce(?, tekst)
                        where notifikasjon_id = ?
                    """
                ) {
                    with(hendelse) {
                        nullableText(lenke)
                        nullableText(tekst)
                        uuid(notifikasjonId)
                    }
                }

                if (hendelse.påminnelse != null) {
                    opprettVarselBestilling(
                        notifikasjonId = hendelse.notifikasjonId,
                        produsentId = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        eksterneVarsler = hendelse.påminnelse.eksterneVarsler,
                        opprinnelse = "KalenderavtaleOppdatert.påminnelse",
                        statusUtsending = "UTSENDING_IKKE_AVGJORT",
                    )
                }

                opprettVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
                    merkelapp = hendelse.merkelapp,
                    eksterneVarsler = hendelse.eksterneVarsler,
                    opprinnelse = "KalenderavtaleOppdatert.eksterneVarsler",
                    statusUtsending = "UTSENDING_BESTILT",
                )
            }
        }
    }

    private fun ParameterSetters.setPåminnelseFelter(
        påminnelse: HendelseModel.Påminnelse?
    ) {
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

            is PåminnelseTidspunkt.FørStartTidspunkt -> {
                text("FørStartTidspunkt")
                periodAsText(tidspunkt.førStartTidpunkt)
            }

            null -> {
                nullableText(null)
                nullableText(null)
            }
        }
        nullableInstantAsText(påminnelse?.tidspunkt?.påminnelseTidspunkt)
    }

    private suspend fun markerIngenUtsendingPåPåminnelseEksterneVarsler(notifikasjonId: UUID) {
        database.nonTransactionalExecuteUpdate(
            """
                        update ekstern_varsel
                        set status_utsending = 'INGEN_UTSENDING'
                        where
                            notifikasjon_id = ?
                            and opprinnelse = 'OppgaveOpprettet.påminnelse'
                            and status_utsending = 'UTSENDING_IKKE_AVGJORT'
                    """
        ) {
            uuid(notifikasjonId)
        }
    }

    private suspend fun updateEksternVarsel(
        eksterneVarselIder: List<UUID>,
        statusUtsending: String,
        feilkode: String? = null,
        timestamp: Instant? = null,
    ) =
        database.nonTransactionalExecuteBatch(
            """
                           update ekstern_varsel
                           set 
                            status_utsending = ?,
                            feilkode = ?,
                            altinn_svar_timestamp = ?
                           where varsel_id = ?
                        """,
            eksterneVarselIder
        ) {
            text(statusUtsending)
            nullableText(feilkode)
            nullableInstantAsText(timestamp)
            uuid(it)
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
                tjeneste_tittel,
                tjeneste_innhold,
                opprinnelse,
                status_utsending
            )
            values
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        nullableText(null)
                        nullableText(null)
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
                        nullableText(null)
                        nullableText(null)
                        text(opprinnelse)
                        text(statusUtsending)
                    }
                }

                is AltinntjenesteVarselKontaktinfo -> with(eksterntVarsel) {
                    uuid(varselId)
                    text("ALTINN_TJENESTE") //varsel_type
                    uuid(notifikasjonId)
                    text(merkelapp)
                    enumAsText(sendevindu)
                    nullableLocalDateTimeAsText(sendeTidspunkt)
                    text(produsentId)
                    nullableText(null)
                    nullableText(null)
                    nullableText(null)
                    nullableText(tittel)
                    nullableText(innhold)
                    text(opprinnelse)
                    text(statusUtsending)
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

        database.nonTransactionalExecuteBatch("""
            insert into ekstern_varsel_mottaker_tjeneste (varsel_id, tjenestekode, tjenesteversjon) 
            values (?, ?, ?)
            on conflict do nothing
        """,
            eksterneVarsler.filterIsInstance<AltinntjenesteVarselKontaktinfo>()
        ) {
            uuid(it.varselId)
            text(it.serviceCode)
            text(it.serviceEdition)
        }
    }
}