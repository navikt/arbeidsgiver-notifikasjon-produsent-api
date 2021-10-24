package no.nav.arbeidsgiver.notifikasjon.statistikk

import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.security.MessageDigest

/* potensielle målinger:
 * - hvor lang tid har det gått før klikk?
 */

class StatistikkModel(
    val database: Database,
) {
    suspend fun antallUtførteHistogram(): List<MultiGauge.Row<Number>> {
        return database.runNonTransactionalQuery(
            """
                WITH alder_tabell AS (
                    select
                        produsent_id, 
                        merkelapp,
                        mottaker,
                        notifikasjon_type,
                        (utfoert_tidspunkt - opprettet_tidspunkt) as alder_sekunder
                    from notifikasjon_statistikk
                )
                select produsent_id, merkelapp, mottaker, notifikasjon_type, '0-1H' as bucket, count(*) as antall
                    from alder_tabell
                    where alder_sekunder < interval '1 hour'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, '1H-1D' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 hour' <= alder_sekunder and alder_sekunder < interval '1 day'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, '1D-3D' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 day' <= alder_sekunder and alder_sekunder < interval '3 day'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, '3D-1W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 day' <= alder_sekunder and alder_sekunder < interval '1 week'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, '1W-2W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 week' <= alder_sekunder and alder_sekunder < interval '2 week'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
                union
                select produsent_id, merkelapp, mottaker, notifikasjon_type, '2W-4W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '2 week' <= alder_sekunder and alder_sekunder < interval '4 week'
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
                union
                select produsent_id,  merkelapp, mottaker, notifikasjon_type, 'infinity' as bucket, count(*) as antall
                    from alder_tabell
                    where alder_sekunder is null
                    group by (produsent_id, merkelapp, mottaker, notifikasjon_type)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "produsent_id", this.getString("produsent_id"),
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "notifikasjon_type", this.getString("notifikasjon_type"),
                        "bucket", this.getString("bucket")
                    ),
                    this.getInt("antall")
                )
            }
        )
    }

    suspend fun antallKlikk(): List<MultiGauge.Row<Number>> {
        return database.runNonTransactionalQuery(
            """
                select 
                    notifikasjon.produsent_id,
                    notifikasjon.merkelapp,
                    notifikasjon.mottaker,
                    notifikasjon.notifikasjon_type,
                    count(*) as antall_klikk
                from notifikasjon_statistikk as notifikasjon
                inner join notifikasjon_statistikk_klikk klikk on notifikasjon.notifikasjon_id = klikk.notifikasjon_id
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
        return database.runNonTransactionalQuery(
            """
                select 
                    produsent_id,
                    merkelapp,
                    mottaker,
                    notifikasjon_type,
                    count(distinct checksum) as antall_unike_tekster
                from notifikasjon_statistikk
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
        return database.runNonTransactionalQuery(
            """
                select produsent_id, merkelapp, mottaker, notifikasjon_type, count(*) as antall
                from notifikasjon_statistikk
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

    suspend fun antallVarsler(): List<MultiGauge.Row<Number>> {
        return database.runNonTransactionalQuery(
            """
                select varsel.produsent_id, merkelapp, status, count(*) as antall
                from varsel_statistikk as varsel
                inner join notifikasjon_statistikk notifikasjon on varsel.notifikasjon_id = notifikasjon.notifikasjon_id
                group by (varsel.produsent_id, merkelapp, status)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "produsent_id", this.getString("produsent_id"),
                        "merkelapp", this.getString("merkelapp"),
                        "status", this.getString("status")
                    ),
                    this.getInt("antall")
                )
            }
        )
    }


    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        when (hendelse) {
            is Hendelse.BeskjedOpprettet -> {
                database.nonTransactionalCommand(
                    """
                    insert into notifikasjon_statistikk 
                        (produsent_id, notifikasjon_id, notifikasjon_type, merkelapp, mottaker, checksum, opprettet_tidspunkt)
                    values (?, ?, 'beskjed', ?, ?, ?, ?)
                    on conflict on constraint notifikasjon_statistikk_pkey do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottaker.typeNavn)
                    string(hendelse.tekst.toHash())
                    timestamptz(hendelse.opprettetTidspunkt)
                }

                if (hendelse.eksterneVarsler.isNotEmpty()) {
                    database.nonTransactionalBatch( // batchCommand
                        """
                        insert into varsel_statistikk_bestilling 
                            (varsel_id, varsel_type, notifikasjon_id, produsent_id, mottaker)
                        values
                            (?, ?, ?, ?, ?)
                        """
                    ) {
                        hendelse.eksterneVarsler.forEach { eksterntVarsel ->
                            when (eksterntVarsel) {
                                is EpostVarselKontaktinfo -> {
                                    uuid(eksterntVarsel.varselId)
                                    string("epost_kontaktinfo")
                                    uuid(hendelse.notifikasjonId)
                                    string(hendelse.produsentId)
                                    string(eksterntVarsel.epostAddr)
                                    addBatch()
                                }
                                is SmsVarselKontaktinfo -> {
                                    uuid(eksterntVarsel.varselId)
                                    string("sms_kontaktinfo")
                                    uuid(hendelse.notifikasjonId)
                                    string(hendelse.produsentId)
                                    string(eksterntVarsel.tlfnr)
                                    addBatch()
                                }
                            }
                        }
                    }
                }
            }
            is Hendelse.OppgaveOpprettet -> {
                database.nonTransactionalCommand(
                    """
                    insert into notifikasjon_statistikk(
                        produsent_id, 
                        notifikasjon_id, 
                        notifikasjon_type, 
                        merkelapp, 
                        mottaker, 
                        checksum, 
                        opprettet_tidspunkt
                    ) 
                    values (?, ?, 'oppgave', ?, ?, ?, ?)
                    on conflict on constraint notifikasjon_statistikk_pkey do nothing;
                    """
                ) {
                    string(hendelse.produsentId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.merkelapp)
                    string(hendelse.mottaker.typeNavn)
                    string(hendelse.tekst.toHash())
                    timestamp_utc(hendelse.opprettetTidspunkt)
                }
                if (hendelse.eksterneVarsler.isNotEmpty()) {
                    database.nonTransactionalBatch( // batchCommand
                        """
                        insert into varsel_statistikk_bestilling 
                            (varsel_id, varsel_type, notifikasjon_id, produsent_id, mottaker)
                        values
                            (?, ?, ?, ?, ?)
                        """
                    ) {
                        hendelse.eksterneVarsler.forEach { eksterntVarsel ->
                            when (eksterntVarsel) {
                                is EpostVarselKontaktinfo -> {
                                    uuid(eksterntVarsel.varselId)
                                    string("epost_kontaktinfo")
                                    uuid(hendelse.notifikasjonId)
                                    string(hendelse.produsentId)
                                    string(eksterntVarsel.epostAddr)
                                    addBatch()
                                }
                                is SmsVarselKontaktinfo -> {
                                    uuid(eksterntVarsel.varselId)
                                    string("sms_kontaktinfo")
                                    uuid(hendelse.notifikasjonId)
                                    string(hendelse.produsentId)
                                    string(eksterntVarsel.tlfnr)
                                    addBatch()
                                }
                            }
                        }
                    }
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
                    values (?, ?, ?)
                    on conflict on constraint notifikasjon_statistikk_klikk_pkey do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    timestamp_utc(metadata.timestamp)
                }
            }
            is Hendelse.EksterntVarselVellykket -> {
                database.nonTransactionalCommand(
                    """
                    insert into varsel_statistikk(
                        hendelse_id, notifikasjon_id, produsent_id, status
                    )
                    values (?, ?, ?, 'vellykket')
                    on conflict on constraint varsel_statistikk_pkey do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.produsentId)
                }
            }
            is Hendelse.EksterntVarselFeilet -> {
                database.nonTransactionalCommand(
                    """
                    insert into varsel_statistikk(
                        hendelse_id, notifikasjon_id, produsent_id, status
                    )
                    values (?, ?, ?, 'feilet')
                    on conflict on constraint varsel_statistikk_pkey do nothing;
                    """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    string(hendelse.produsentId)
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