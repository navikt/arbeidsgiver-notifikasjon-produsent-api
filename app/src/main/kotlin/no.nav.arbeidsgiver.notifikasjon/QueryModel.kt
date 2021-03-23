package no.nav.arbeidsgiver.notifikasjon

import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("query-model-builder-processor")

data class Koordinat(
    val mottaker: Mottaker,
    val merkelapp: String,
    val eksternId: String,
)

data class Beskjed(
    val merkelapp: String,
    val tekst: String,
    val grupperingsid: String? = null,
    val lenke: String,
    val eksternId: String,
    val mottaker: Mottaker,
    val opprettetTidspunkt: String
)

private val repository = mutableMapOf<Koordinat, Beskjed>()


fun f(key: KafkaKey, event: Event): Beskjed {
    Beskjed(
        merkelapp: String,
        tekst: String,
        grupperingsid: String? = null,
        lenke: String,
        eksternId: String,
        mottaker: Mottaker,
        opprettetTidspunkt: String
    )
}

fun queryModelBuilderProcessor(key: KafkaKey, event: Event) {
    val koordinat = Koordinat(
        mottaker = key.mottaker,
        merkelapp = event.merkelapp,
        eksternId = event.eksternId
    )
}