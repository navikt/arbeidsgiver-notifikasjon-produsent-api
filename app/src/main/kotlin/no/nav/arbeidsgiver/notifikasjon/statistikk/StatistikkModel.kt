package no.nav.arbeidsgiver.notifikasjon.statistikk

import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.security.MessageDigest

/* potensielle målinger:
- tid mellom oppgave ny -> utført
  påvirker klikk hvor kort tid det er?
- hvor lang tid har det gått før klikk?
 */

class StatistikkModel(
    val database: Database,
) {
    suspend fun antallUtførteHistogram(): List<MultiGauge.Row<Number>> {
        return database.runNonTransactionalQuery(
            """
                WITH alder_tabell AS (
                    select
                        merkelapp,
                        mottaker,
                        hendelse_type,
                        (utfoert_tidspunkt - opprettet_tidspunkt) as alder_sekunder
                    from notifikasjon_statistikk
                )
                select merkelapp, mottaker, hendelse_type, '0-1time' as bucket, count(*) as antall
                    from alder_tabell
                    where alder_sekunder < interval '1 hour'
                    group by (merkelapp, mottaker, hendelse_type)
                union
                select merkelapp, mottaker, hendelse_type, '1H-1D' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 hour' <= alder_sekunder and alder_sekunder < interval '1 day'
                    group by (merkelapp, mottaker, hendelse_type)
                union
                select merkelapp, mottaker, hendelse_type, '1D-3D' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 day' <= alder_sekunder and alder_sekunder < interval '3 day'
                    group by (merkelapp, mottaker, hendelse_type)
                union
                select merkelapp, mottaker, hendelse_type, '3D-1W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 day' <= alder_sekunder and alder_sekunder < interval '1 week'
                    group by (merkelapp, mottaker, hendelse_type)
                union
                select merkelapp, mottaker, hendelse_type, '1W-2W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '1 week' <= alder_sekunder and alder_sekunder < interval '2 week'
                    group by (merkelapp, mottaker, hendelse_type)
                union
                select merkelapp, mottaker, hendelse_type, '2W-4W' as bucket, count(*) as antall
                    from alder_tabell
                    where interval '2 week' <= alder_sekunder and alder_sekunder < interval '4 week'
                    group by (merkelapp, mottaker, hendelse_type)
                union
                select merkelapp, mottaker, hendelse_type, 'infinity' as bucket, count(*) as antall
                    from alder_tabell
                    where alder_sekunder is null
                    group by (merkelapp, mottaker, hendelse_type)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "hendelse_type", this.getString("hendelse_type"),
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
                    notifikasjon.merkelapp,
                    notifikasjon.mottaker,
                    notifikasjon.hendelse_type,
                    count(*) as antall_klikk
                from notifikasjon_statistikk as notifikasjon
                inner join notifikasjon_statistikk_klikk klikk on notifikasjon.notifikasjon_id = klikk.notifikasjon_id
                group by (merkelapp, mottaker, hendelse_type)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "hendelse_type", this.getString("hendelse_type")
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
                    merkelapp,
                    mottaker,
                    hendelse_type,
                    count(distinct checksum) as antall_unike_tekster
                from notifikasjon_statistikk
                group by (merkelapp, mottaker, hendelse_type)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "hendelse_type", this.getString("hendelse_type")
                    ),
                    this.getInt("antall_unike_tekster")
                )
            }
        )
    }

    suspend fun antallNotifikasjoner(): List<MultiGauge.Row<Number>> {
        return database.runNonTransactionalQuery(
            """
                select merkelapp, mottaker, hendelse_type, count(*) as antall
                from notifikasjon_statistikk
                group by (merkelapp, mottaker, hendelse_type)
            """,
            transform = {
                MultiGauge.Row.of(
                    Tags.of(
                        "merkelapp", this.getString("merkelapp"),
                        "mottaker", this.getString("mottaker"),
                        "hendelse_type", this.getString("hendelse_type")
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