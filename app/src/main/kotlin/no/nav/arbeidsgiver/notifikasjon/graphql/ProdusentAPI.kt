package no.nav.arbeidsgiver.notifikasjon.graphql

import graphql.GraphQL
import graphql.schema.DataFetcher
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.*
import org.apache.kafka.clients.producer.Producer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*


private val log = LoggerFactory.getLogger("GraphQL.ProdusentAPI")!!

private val whoamiQuery = DataFetcher {
    it.getContext<Context>().produsentId
}

data class FnrmottakerInput (
    val fodselsnummer: String,
    val virksomhetsnummer: String
) {
    fun tilDomene(): Mottaker =
        FodselsnummerMottaker(
            fodselsnummer = fodselsnummer,
            virksomhetsnummer = virksomhetsnummer
        )
}

data class AltinnmottakerInput(
    val altinntjenesteKode: String,
    val altinntjenesteVersjon: String,
    val virksomhetsnummer: String,
) {
    fun tilDomene(): Mottaker =
        AltinnMottaker(
            altinntjenesteKode = altinntjenesteKode,
            altinntjenesteVersjon = altinntjenesteVersjon,
            virksomhetsnummer = virksomhetsnummer
        )
}

data class MottakerInput(
    val altinn: AltinnmottakerInput?,
    val fnr: FnrmottakerInput?
) {

    fun tilDomene(): Mottaker {
        return if (altinn != null && fnr == null) {
            altinn.tilDomene()
        } else if (fnr != null && altinn == null) {
            fnr.tilDomene()
        } else {
            throw IllegalArgumentException("Ugyldig mottaker")
        }
    }
}

data class BeskjedInput(
    val merkelapp: String,
    val tekst: String,
    val grupperingsid: String?,
    val lenke: String,
    val eksternId: String,
    val mottaker: MottakerInput,
    val opprettetTidspunkt: String = Instant.now().toString()
) {
    fun tilDomene(guid: UUID): BeskjedOpprettet =
        BeskjedOpprettet(
            guid = guid,
            merkelapp = merkelapp,
            tekst = tekst,
            grupperingsid = grupperingsid,
            lenke = lenke,
            eksternId = eksternId,
            mottaker = mottaker.tilDomene(),
            opprettetTidspunkt = opprettetTidspunkt
        )
}

data class BeskjedResultat(
    val id: String
)

private fun nyBeskjedMutation(kafkaProducer: Producer<KafkaKey, Event>) = DataFetcher {
    val nyBeskjed = it.getTypedArgument<BeskjedInput>("nyBeskjed")
    val id = UUID.randomUUID()
    log.info("mottatt ny beskjed, id: $id, beskjed: $nyBeskjed")
    kafkaProducer.beskjedOpprettet(nyBeskjed.tilDomene(id))
    BeskjedResultat(id.toString())
}

fun produsentGraphQL(kafkaProducer: Producer<KafkaKey, Event> = createProducer()): GraphQL =
    createGraphQL("/produsent.graphqls") {

        wire("Query") {
            dataFetcher("ping") {
                "pong"
            }

            dataFetcher("whoami", whoamiQuery)
        }

        wire("Mutation") {
            dataFetcher("nyBeskjed", nyBeskjedMutation(kafkaProducer))
        }
    }