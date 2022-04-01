package no.nav.arbeidsgiver.notifikasjon.statistikk

import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnReporteeMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnRolleMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/* potensielle målinger:
 * - hvor lang tid har det gått før klikk?
 */

class StatistikkModel(
    val database: Database,
) {
    val log = logger()
    suspend fun antallKlikketPaa(): List<MultiGauge.Row<Number>> {
        return database.nonTransactionalExecuteQuery(
            """
                select
                    notifikasjon.produsent_id,
                    notifikasjon.merkelapp,
                    notifikasjon.mottaker,
                    notifikasjon.notifikasjon_type,
                    count(distinct notifikasjon.notifikasjon_id) as antall_klikket_paa
                from notifikasjon
                inner join notifikasjon_klikk klikk on notifikasjon.notifikasjon_id = klikk.notifikasjon_id
                group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
            """
        ) {
            MultiGauge.Row.of(
                Tags.of(
                    "produsent_id", this.getString("produsent_id"),
                    "merkelapp", this.getString("merkelapp"),
                    "mottaker", this.getString("mottaker"),
                    "notifikasjon_type", this.getString("notifikasjon_type")
                ),
                this.getInt("antall_klikket_paa")
            )
        }

    }

    suspend fun antallUtførteHistogram(): List<MultiGauge.Row<Number>> {
        return database.nonTransactionalExecuteQuery(
            """
               WITH alder_tabell AS (
                    select produsent_id,
                           merkelapp,
                           mottaker,
                           notifikasjon_type,
                           (utfoert_tidspunkt - opprettet_tidspunkt) as alder_sekunder,
                           (case
                                when exists(select 1 from notifikasjon_klikk klikk where notifikasjon_id = notifikasjon.notifikasjon_id)
                                    then true
                                else false end)                      as klikket_paa
                    from notifikasjon
                    where utfoert_tidspunkt is not null)
                select produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa, '0-1H' as bucket, count(*) as antall
                    from alder_tabell
                    where alder_sekunder < interval '1 hour'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa, '1H-1D' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 hour' <= alder_sekunder and alder_sekunder < interval '1 day'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa, '1D-3D' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 day' <= alder_sekunder and alder_sekunder < interval '3 day'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa, '3D-1W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 day' <= alder_sekunder and alder_sekunder < interval '1 week'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa, '1W-2W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 week' <= alder_sekunder and alder_sekunder < interval '2 week'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa, '2W-4W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '2 week' <= alder_sekunder and alder_sekunder < interval '4 week'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa, '4W-infinity' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '4 week' <= alder_sekunder
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type, klikket_paa)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "produsent_id", this.getString("produsent_id"),
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "notifikasjon_type", this.getString("notifikasjon_type"),
                        "bucket", this.getString("bucket"),
                        "klikket_paa", this.getString("klikket_paa")
                    ),
                    this.getInt("antall")
                )
            }
        )
    }

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

    suspend fun antallUnikeTekster(): List<MultiGauge.Row<Number>> {
        return database.nonTransactionalExecuteQuery(
            """
                select 
                    produsent_id,
                    merkelapp,
                    mottaker,
                    notifikasjon_type,
                    count(distinct checksum) as antall_unike_tekster
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
                    this.getInt("antall_unike_tekster")
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
                    merkelapp, 
                    varsel_type, 
                    coalesce(status, 'bestilt') as status, 
                    coalesce(feilkode, '') as feilkode,
                    count(*) as antall
                from varsel_bestilling as bestilling
                         inner join notifikasjon on bestilling.notifikasjon_id = notifikasjon.notifikasjon_id
                         left outer join varsel_resultat as resultat on resultat.varsel_id = bestilling.varsel_id
                group by (notifikasjon.produsent_id, merkelapp, varsel_type, status, feilkode)
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
        val ignore : Any = when (hendelse) {
            is BeskjedOpprettet -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon 
                        (produsent_id, notifikasjon_id, notifikasjon_type, merkelapp, mottaker, checksum, opprettet_tidspunkt)
                    values (?, ?, 'beskjed', ?, ?, ?, ?)
                    on conflict on constraint notifikasjon_pkey do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottakere.oppsummering())
                    string(hendelse.tekst.toHash())
                    timestamptz(hendelse.opprettetTidspunkt)
                }

                oppdaterVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
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
                        opprettet_tidspunkt
                    )
                    values
                    (?, ?, 'oppgave', ?, ?, ?, ?)
                    on conflict on constraint notifikasjon_pkey do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottakere.oppsummering())
                    string(hendelse.tekst.toHash())
                    timestamp_utc(hendelse.opprettetTidspunkt)
                }

                oppdaterVarselBestilling(
                    notifikasjonId = hendelse.notifikasjonId,
                    produsentId = hendelse.produsentId,
                    iterable = hendelse.eksterneVarsler
                )
            }
            is OppgaveUtført -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    update notifikasjon 
                        set utfoert_tidspunkt = ?
                        where notifikasjon_id = ?
                    """
                ) {
                    timestamp_utc(metadata.timestamp)
                    uuid(hendelse.notifikasjonId)
                }
            }
            is BrukerKlikket -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into notifikasjon_klikk 
                        (hendelse_id, notifikasjon_id, klikket_paa_tidspunkt)
                    values (?, ?, ?)
                    on conflict on constraint notifikasjon_klikk_pkey do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    timestamp_utc(metadata.timestamp)
                }
            }
            is EksterntVarselVellykket -> {
                database.nonTransactionalExecuteUpdate(
                    """
                    insert into varsel_resultat 
                        (hendelse_id, varsel_id, notifikasjon_id, produsent_id, status)
                    values
                    (?, ?, ?, ?, 'vellykket')
                    on conflict on constraint varsel_resultat_pkey do nothing;
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
                    on conflict on constraint varsel_resultat_pkey do nothing;
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
                    timestamp_utc(hendelse.deletedAt)
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
                    on conflict on constraint sak_pkey do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.sakId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottakere.oppsummering())
                    timestamptz(OffsetDateTime.now(ZoneId.systemDefault())) // TODO: set faktisk tidspunkt
                }
            }
            is NyStatusSak -> {
                log.error("mottok hendelse som ikke enda er støttet {}", hendelse.javaClass.simpleName)
            }
        }
    }

    private suspend fun oppdaterVarselBestilling(
        notifikasjonId: UUID,
        produsentId: String,
        iterable: List<EksterntVarsel>,
    ) {
        database.nonTransactionalExecuteBatch(
            """
            insert into varsel_bestilling 
                (varsel_id, varsel_type, notifikasjon_id, produsent_id, mottaker)
            values
                (?, ?, ?, ?, ?)
            on conflict (varsel_id) do nothing;
            """,
            iterable
        ) { eksterntVarsel ->
            when (eksterntVarsel) {
                is EpostVarselKontaktinfo -> {
                    uuid(eksterntVarsel.varselId)
                    string("epost_kontaktinfo")
                    uuid(notifikasjonId)
                    string(produsentId)
                    string(eksterntVarsel.epostAddr)
                }
                is SmsVarselKontaktinfo -> {
                    uuid(eksterntVarsel.varselId)
                    string("sms_kontaktinfo")
                    uuid(notifikasjonId)
                    string(produsentId)
                    string(eksterntVarsel.tlfnr)
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