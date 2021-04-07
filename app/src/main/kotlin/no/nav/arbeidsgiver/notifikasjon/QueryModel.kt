package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.hendelse.*
import org.slf4j.LoggerFactory
import java.time.Instant

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
    val opprettetTidspunkt: Instant,
)

val repository = mutableMapOf<Koordinat, QueryBeskjed>()

data class Tilgang(
    val virksomhet: String,
    val servicecode: String,
    val serviceedition: String,
)

fun hentNotifikasjoner(fnr: String, tilganger: Collection<Tilgang>): List<QueryBeskjed> {
    return repository.values.filter { beskjed ->
        when (val mottaker = beskjed.mottaker) {
            is FodselsnummerMottaker ->
                mottaker.fodselsnummer == fnr
            is AltinnMottaker -> tilganger.any { tilgang ->
                mottaker.altinntjenesteKode == tilgang.servicecode &&
                        mottaker.altinntjenesteVersjon == tilgang.serviceedition &&
                        mottaker.virksomhetsnummer == tilgang.virksomhet
            }
        }
    }
}

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
                opprettetTidspunkt = event.opprettetTidspunkt,
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