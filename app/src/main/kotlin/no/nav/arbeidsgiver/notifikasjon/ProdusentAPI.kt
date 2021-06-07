package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import java.util.*

object ProdusentAPI {
    private val log = logger()

    data class Context(
        val produsentid: String,
        override val coroutineScope: CoroutineScope
    ): WithCoroutineScope

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
        fun tilDomene(id: UUID): Hendelse.BeskjedOpprettet {
            val mottaker = mottaker.tilDomene()
            return Hendelse.BeskjedOpprettet(
                id = id,
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
        val id: UUID? = null,
        val errors: List<MutationError> = emptyList()
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class MutationError {
        abstract val feilmelding: String

        @JsonTypeName("UgyldigMerkelapp")
        data class UgyldigMerkelapp(
            override val feilmelding: String
        ) : MutationError()

        @JsonTypeName("UgyldigMottaker")
        data class UgyldigMottaker(
            override val feilmelding: String
        ) : MutationError()
    }

    fun newGraphQL(
        kafkaProducer: CoroutineProducer<KafkaKey, Hendelse> = createKafkaProducer(),
        produsentRegister: ProdusentRegister = ProdusentRegisterImpl
    ) = TypedGraphQL<Context>(
        createGraphQL("/produsent.graphqls") {
            directive("Validate", ValidateDirective)

            scalar(Scalars.ISO8601DateTime)

            resolveSubtypes<MutationError>()

            wire("Query") {
                dataFetcher("ping") {
                    "pong"
                }

                dataFetcher("whoami") {
                    it.getContext<Context>().produsentid
                }
            }

            wire("Mutation") {
                coDataFetcher("nyBeskjed")  { env ->
                    val nyBeskjed = env.getTypedArgument<BeskjedInput>("nyBeskjed")
                    val context = env.getContext<Context>()
                    val produsentDefinisjon = produsentRegister.finn(context.produsentid)
                    val errors = mutableListOf<MutationError>()

                    if (!produsentDefinisjon.harTilgangTil(nyBeskjed.mottaker.tilDomene())) {
                        errors += MutationError.UgyldigMottaker("""
                                | Ugyldig mottaker '${nyBeskjed.mottaker}' for produsent '${produsentDefinisjon.id}'. 
                                | Gyldige mottakere er: ${produsentDefinisjon.mottakere}
                                """.trimMargin()
                        )
                    }

                    if (!produsentDefinisjon.harTilgangTil(nyBeskjed.merkelapp)) {
                        errors += MutationError.UgyldigMerkelapp("""
                                | Ugyldig merkelapp '${nyBeskjed.merkelapp}' for produsent '${produsentDefinisjon.id}'. 
                                | Gyldige merkelapper er: ${produsentDefinisjon.merkelapper}
                                """.trimMargin()
                        )
                    }

                    if (errors.isNotEmpty()) {
                        return@coDataFetcher BeskjedResultat(errors = errors)
                    }

                    val id = UUID.randomUUID()
                    log.info("mottatt ny beskjed, id: $id, beskjed: $nyBeskjed")
                    kafkaProducer.beskjedOpprettet(nyBeskjed.tilDomene(id))
                    return@coDataFetcher BeskjedResultat(id)
                }
            }
        }
    )
}
