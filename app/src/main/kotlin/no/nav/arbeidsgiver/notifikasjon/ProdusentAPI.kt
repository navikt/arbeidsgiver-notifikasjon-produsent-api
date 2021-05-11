package no.nav.arbeidsgiver.notifikasjon

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.apache.kafka.clients.producer.Producer
import java.time.OffsetDateTime
import java.util.*

object ProdusentAPI {
    private val log = logger()

    data class Context(
        val payload: Payload,
        val subject: String = payload.subject,
        val produsentId: String = "iss:${payload.issuer} sub:${payload.subject}"
    )

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
        fun tilDomene(guid: UUID): Hendelse.BeskjedOpprettet {
            val mottaker = mottaker.tilDomene()
            return Hendelse.BeskjedOpprettet(
                guid = guid,
                merkelapp = merkelapp,
                tekst = tekst,
                grupperingsid = grupperingsid,
                lenke = lenke,
                eksternId = eksternId,
                mottaker = mottaker,
                opprettetTidspunkt = opprettetTidspunkt,
                virksomhetsnummer = when (mottaker) {
                    is FodselsnummerMottaker -> mottaker.virksomhetsnummer
                    is AltinnMottaker -> mottaker.virksomhetsnummer
                }
            )
        }
    }

    data class BeskjedResultat(
        val id: String? = null,
        val errors: List<MutationError> = emptyList()
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class MutationError {
        abstract val feilmelding: String

        @JsonTypeName("UgyldigMerkelapp")
        data class UgyldigMerkelapp(
            override val feilmelding: String
        ) : MutationError()
    }


    private fun nyBeskjedMutation(kafkaProducer: Producer<KafkaKey, Hendelse>) = DataFetcher {
        val nyBeskjed = it.getTypedArgument<BeskjedInput>("nyBeskjed")
        val produsentMerkelapp = ProdusentRegister.finn(it.getContext<Context>().subject)
        if (!produsentMerkelapp.har(nyBeskjed.merkelapp)) {
            BeskjedResultat(
                errors = listOf(
                    MutationError.UgyldigMerkelapp("""
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

    fun newGraphQL(
        kafkaProducer: Producer<KafkaKey, Hendelse> = createKafkaProducer()
    ) = TypedGraphQL<Context>(
        createGraphQL("/produsent.graphqls") {

            scalar(Scalars.ISO8601DateTime)

            subtypes<MutationError>("MutationError") {
                when (it) {
                    is MutationError.UgyldigMerkelapp -> "UgyldigMerkelapp"
                }
            }

            wire("Query") {
                dataFetcher("ping") {
                    "pong"
                }

                dataFetcher("whoami") {
                    it.getContext<Context>().produsentId
                }
            }

            wire("Mutation") {
                dataFetcher("nyBeskjed", nyBeskjedMutation(kafkaProducer))
            }
        }
    )
}