package no.nav.arbeidsgiver.notifikasjon

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.annotation.JsonTypeInfo
import graphql.schema.DataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.apache.kafka.clients.producer.Producer
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.*

data class ProdusentContext(
    val payload: Payload,
    val subject: String = payload.subject,
    val produsentId: String = "iss:${payload.issuer} sub:${payload.subject}"
)

private val log = LoggerFactory.getLogger("GraphQL.ProdusentAPI")!!

private val whoamiQuery = DataFetcher {
    it.getContext<ProdusentContext>().produsentId
}

data class FnrmottakerInput(
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
    val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now()
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
    val id: String? = null,
    val errors: List<MutationError> = emptyList()
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
sealed class MutationError {
    abstract val feilmelding: String
}
data class UgyldigMerkelapp(override val feilmelding: String) : MutationError()

private fun nyBeskjedMutation(kafkaProducer: Producer<KafkaKey, Event>) = DataFetcher {
    val nyBeskjed = it.getTypedArgument<BeskjedInput>("nyBeskjed")
    val produsentMerkelapp = ProdusentRegister.finn(it.getContext<ProdusentContext>().subject)
    if (!produsentMerkelapp.har(nyBeskjed.merkelapp)) {
        BeskjedResultat(
            errors = listOf(
                UgyldigMerkelapp("""
                | Ugyldig merkelapp '${nyBeskjed.merkelapp}' for produsent '${produsentMerkelapp.produsent}'. 
                | Gyldige merkelapper er: ${produsentMerkelapp.merkelapper}
                """.trimMargin())
            )
        )
    } else {
        val id = UUID.randomUUID()
        log.info("mottatt ny beskjed, id: $id, beskjed: $nyBeskjed")
        kafkaProducer.beskjedOpprettet(nyBeskjed.tilDomene(id))
        BeskjedResultat(id.toString())
    }
}

fun createProdusentGraphQL(
    kafkaProducer: Producer<KafkaKey, Event> = createKafkaProducer()
) = TypedGraphQL<ProdusentContext>(
    createGraphQL("/produsent.graphqls") {

        scalar(Scalars.ISO8601DateTime)

        wire("Query") {
            dataFetcher("ping") {
                "pong"
            }

            dataFetcher("whoami", whoamiQuery)
        }

        wire("Mutation") {
            dataFetcher("nyBeskjed", nyBeskjedMutation(kafkaProducer))
        }

        type("MutationError") {
            it.typeResolver { env ->
                env.schema.getObjectType(
                    when (env.getObject<MutationError>()) {
                        is UgyldigMerkelapp -> "UgyldigMerkelapp"
                    }
                )
            }
        }

    }
)

