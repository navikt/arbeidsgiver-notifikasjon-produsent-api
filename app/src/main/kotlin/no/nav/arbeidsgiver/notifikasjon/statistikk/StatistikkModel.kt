package no.nav.arbeidsgiver.notifikasjon.statistikk

import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
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
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NesteStegSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.TilleggsinformasjonSak
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.security.MessageDigest
import java.time.ZoneOffset.UTC
import java.util.*

/* potensielle målinger:
 * - hvor lang tid har det gått før klikk?
 */

class StatistikkModel(
    val database: Database,
) {
    val log = logger()

    suspend fun antallKlikk(): List<MultiGauge.Row<Number>> {
        return database.nonTransactionalExecuteQuery(
            """
                select 
                    notifikasjon.produsent_id,
                    notifikasjon.merkelapp,
                    notifikasjon.mottaker,
                    notifikasjon.notifikasjon_type,
                    count(*) as antall_klikk
                from notifikasjon
                inner join notifikasjon_klikk klikk on notifikasjon.notifikasjon_id = klikk.notifikasjon_id
                group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "produsent_id", this.getString("produsent_id"),
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "notifikasjon_type", this.getString("notifikasjon_type")
                    ),
                    this.getInt("antall_klikk")
                )
            }
        )
    }

    suspend fun antallNotifikasjoner(): List<MultiGauge.Row<Number>> {
        return database.nonTransactionalExecuteQuery(
            """
                select produsent_id, merkelapp, mottaker, notifikasjon_type, count(*) as antall
                from notifikasjon
                group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "produsent_id", this.getString("produsent_id"),
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "notifikasjon_type", this.getString("notifikasjon_type")
                    ),
                    this.getInt("antall")
                )
            }
        )
    }

    suspend fun antallSaker(): List<MultiGauge.Row<Number>> {
        return database.nonTransactionalExecuteQuery(
            """
                select produsent_id, merkelapp, mottaker, count(*) as antall
                from sak
                group by (produsent_id, merkelapp, mottaker)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "produsent_id", this.getString("produsent_id"),
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                    ),
                    this.getInt("antall")
                )
            }
        )
    }

    suspend fun antallVarsler(): List<MultiGauge.Row<Number>> {
        return database.nonTransactionalExecuteQuery(
            """
                select 
                    notifikasjon.produsent_id, 
                    notifikasjon.merkelapp, 
                    varsel_type, 
                    coalesce(status, 'bestilt') as status, 
                    coalesce(feilkode, '') as feilkode,
                    count(*) as antall
                from varsel_bestilling as bestilling
                         inner join notifikasjon on bestilling.notifikasjon_id = notifikasjon.notifikasjon_id
                         left outer join varsel_resultat as resultat on resultat.varsel_id = bestilling.varsel_id
                group by (notifikasjon.produsent_id, notifikasjon.merkelapp, varsel_type, status, feilkode)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "produsent_id", this.getString("produsent_id"),
                        "merkelapp", this.getString("merkelapp"),
                        "varsel_type", this.getString("varsel_type"),
                        "status", this.getString("status"),
                        "feilkode", this.getString("feilkode"),
                    ),
                    this.getInt("antall")
                )
            }
        )
    }


    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        when (hendelse) {
            is KalenderavtaleOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon 
                        (produsent_id, notifikasjon_id, gruppering_id, notifikasjon_type, merkelapp, mottaker, checksum, opprettet_tidspunkt)
                    values (?, ?, ?, 'kalenderavtale', ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    with(hendelse) {
                        text(produsentId)
                        uuid(notifikasjonId)
                        nullableText(grupperingsid)
                        text(merkelapp)
                        text(mottakere.oppsummering())
                        text(tekst.toHash())
                        timestamp_with_timezone(opprettetTidspunkt)
                    }
                }
            }
            is HendelseModel.KalenderavtaleOppdatert -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    update notifikasjon set checksum = coalesce(?, checksum) where notifikasjon_id = ?
                    """
                ) {
                    with(hendelse) {
                        nullableText(tekst?.toHash())
                        uuid(notifikasjonId)
                    }
                }
            }

            is BeskjedOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon 
                        (produsent_id, notifikasjon_id, gruppering_id, notifikasjon_type, merkelapp, mottaker, checksum, opprettet_tidspunkt)
                    values (?, ?, ?, 'beskjed', ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    text(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    nullableText(hendelse.grupperingsid)
                    hendelse.grupperingsid
                    text(hendelse.merkelapp)
                    text(hendelse.mottakere.oppsummering())
                    text(hendelse.tekst.toHash())
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
                        gruppering_id,
                        notifikasjon_type, 
                        merkelapp, 
                        mottaker, 
                        checksum, 
                        opprettet_tidspunkt,
                        frist
                    )
                    values
                    (?, ?, ?, 'oppgave', ?, ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    text(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    nullableText(hendelse.grupperingsid)
                    text(hendelse.merkelapp)
                    text(hendelse.mottakere.oppsummering())
                    text(hendelse.tekst.toHash())
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
                    text(hendelse.produsentId)
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
                    text(hendelse.produsentId)
                    text(hendelse.altinnFeilkode)
                }
            }

            is HendelseModel.EksterntVarselKansellert -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into varsel_resultat 
                        (hendelse_id, varsel_id, notifikasjon_id, produsent_id, status)
                    values
                        (?, ?, ?, ?, 'kansellert')
                    on conflict do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.varselId)
                    uuid(hendelse.notifikasjonId)
                    text(hendelse.produsentId)
                }
            }

            is SoftDelete -> {
                database.transaction {
                    if (hendelse.grupperingsid != null && hendelse.merkelapp != null) {
                        executeUpdate(
                            """
                        update notifikasjon 
                            set soft_deleted_tidspunkt = ?
                            where gruppering_id = ? and merkelapp = ?
                        """
                        ) {
                            timestamp_without_timezone_utc(hendelse.deletedAt)
                            text(hendelse.grupperingsid)
                            text(hendelse.merkelapp)
                        }
                    }
                    executeUpdate(
                        """
                    update notifikasjon 
                        set soft_deleted_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                    ) {
                        timestamp_without_timezone_utc(hendelse.deletedAt)
                        uuid(hendelse.aggregateId)
                    }
                    executeUpdate(
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
            }

            is HardDelete -> {
                database.transaction {
                    if (hendelse.grupperingsid != null && hendelse.merkelapp != null) {
                        executeUpdate(
                            """
                        update notifikasjon 
                            set hard_deleted_tidspunkt = ?
                            where gruppering_id = ? and merkelapp = ?
                        """
                        ) {
                            timestamp_without_timezone_utc(hendelse.deletedAt)
                            text(hendelse.grupperingsid)
                            text(hendelse.merkelapp)
                        }
                    }
                    executeUpdate(
                        """
                    update notifikasjon 
                        set hard_deleted_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                    ) {
                        timestamp_without_timezone_utc(hendelse.deletedAt)
                        uuid(hendelse.aggregateId)
                    }
                    executeUpdate(
                        """
                    update sak 
                        set hard_deleted_tidspunkt = ?
                        where sak_id = ?
                    """
                    ) {
                        timestamp_without_timezone_utc(hendelse.deletedAt)
                        uuid(hendelse.aggregateId)
                    }
                }
            }

            is SakOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into sak 
                        (produsent_id, sak_id, gruppering_id, merkelapp, mottaker, opprettet_tidspunkt)
                    values (?, ?, ?, ?, ?, ?)
                    on conflict do nothing;
                    """
                ) {
                    text(hendelse.produsentId)
                    uuid(hendelse.sakId)
                    nullableText(hendelse.grupperingsid)
                    text(hendelse.merkelapp)
                    text(hendelse.mottakere.oppsummering())
                    timestamp_with_timezone(hendelse.opprettetTidspunkt(metadata.timestamp.atOffset(UTC)))
                }
            }

            is NyStatusSak -> {
                // noop
            }

            is PåminnelseOpprettet -> {
                // noop
            }

            is NesteStegSak -> {
                // noop
            }

            is TilleggsinformasjonSak -> {
                // noop
            }

            is FristUtsatt -> {
                database.nonTransactionalExecuteUpdate(
                    """
                        update notifikasjon
                        set frist = greatest(?, frist)
                        where notifikasjon_id = ?
                    """
                ) {
                    date(hendelse.frist)
                    uuid(hendelse.notifikasjonId)
                }
            }

            is HendelseModel.PaaminnelseEndret -> TODO()
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
                    text("epost_kontaktinfo")
                    uuid(notifikasjonId)
                    text(produsentId)
                    text(merkelapp)
                    text(eksterntVarsel.epostAddr)
                    text((eksterntVarsel.tittel + eksterntVarsel.htmlBody).toHash())
                }

                is SmsVarselKontaktinfo -> {
                    uuid(eksterntVarsel.varselId)
                    text("sms_kontaktinfo")
                    uuid(notifikasjonId)
                    text(produsentId)
                    text(merkelapp)
                    text(eksterntVarsel.tlfnr)
                    text(eksterntVarsel.smsTekst.toHash())
                }

                is AltinntjenesteVarselKontaktinfo -> {
                    uuid(eksterntVarsel.varselId)
                    text("altinntjeneste_kontaktinfo")
                    uuid(notifikasjonId)
                    text(produsentId)
                    text(merkelapp)
                    text("${eksterntVarsel.serviceCode}:${eksterntVarsel.serviceEdition}")
                    text((eksterntVarsel.tittel + eksterntVarsel.innhold).toHash())
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
            is HendelseModel._AltinnRolleMottaker -> basedOnEnv(
                prod = { throw RuntimeException("AltinnRolleMottaker støttes ikke i prod") },
                other = { "AltinnRolleMottaker" },
            )

            is HendelseModel._AltinnReporteeMottaker -> basedOnEnv(
                prod = { throw RuntimeException("AltinnReporteeMottaker støttes ikke i prod") },
                other = { "AltinnReporteeMottaker" },
            )
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