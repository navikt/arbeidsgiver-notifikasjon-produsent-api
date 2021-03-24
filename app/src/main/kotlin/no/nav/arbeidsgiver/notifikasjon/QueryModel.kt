package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.hendelse.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.Event
import no.nav.arbeidsgiver.notifikasjon.hendelse.Mottaker
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("query-model-builder-processor")

data class Koordinat(
    val mottaker: Mottaker,
    val merkelapp: String,
    val eksternId: String,
)

data class QueryBeskjed(
    val merkelapp: String,
    val tekst: String,
    val grupperingsid: String? = null,
    val lenke: String,
    val eksternId: String,
    val mottaker: Mottaker,
    val opprettetTidspunkt: String
)

val repository = mutableMapOf<Koordinat, QueryBeskjed>()

fun tilQueryBeskjed(event: Event): QueryBeskjed =
    when (event) {
        is BeskjedOpprettet ->
            QueryBeskjed(
                merkelapp = event.merkelapp,
                tekst = event.tekst,
                grupperingsid = event.grupperingsid,
                lenke = event.lenke,
                eksternId = event.eksternId,
                mottaker = event.mottaker,
                opprettetTidspunkt = event.opprettetTidspunkt.toString()
            )
    }

fun QueryBeskjed?.oppdatertMed(nyBeskjed: QueryBeskjed): QueryBeskjed {
    return if (this == null) {
        nyBeskjed
    } else if (this == nyBeskjed) {
        this
    } else {
        log.error("forsøk på å endre eksisterende beskjed")
        this
    }
}

fun queryModelBuilderProcessor(event: Event) {
    val koordinat = Koordinat(
        mottaker = event.mottaker,
        merkelapp = event.merkelapp,
        eksternId = event.eksternId
    )

    repository[koordinat] = repository[koordinat].oppdatertMed(tilQueryBeskjed(event))
}