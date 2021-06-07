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

    data class NaermesteLederMottakerInput(
        val naermesteLederFnr: String,
        val ansattFnr: String,
        val virksomhetsnummer: String
    ) {
        fun tilDomene(): Mottaker =
            NærmesteLederMottaker(
                naermesteLederFnr = naermesteLederFnr,
                ansattFnr = ansattFnr,
                virksomhetsnummer = virksomhetsnummer
            )
    }

    data class AltinnMottakerInput(
        val serviceCode: String,
        val serviceEdition: String,
        val virksomhetsnummer: String,
    ) {
        fun tilDomene(): Mottaker =
            AltinnMottaker(
                serviceCode = serviceCode,
                serviceEdition = serviceEdition,
                virksomhetsnummer = virksomhetsnummer
            )
    }

    data class MottakerInput(
        val altinn: AltinnMottakerInput?,
        val naermesteLeder: NaermesteLederMottakerInput?
    ) {

        fun tilDomene(): Mottaker {
            return if (altinn != null && naermesteLeder == null) {
                altinn.tilDomene()
            } else if (naermesteLeder != null && altinn == null) {
                naermesteLeder.tilDomene()
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
                    is NærmesteLederMottaker -> mottaker.virksomhetsnummer
                    is AltinnMottaker -> mottaker.virksomhetsnummer
                }
            )
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyBeskjedResultat

    @JsonTypeName("NyBeskjedVellykket")
    data class NyBeskjedVellykket(
        val id: UUID
    ) : NyBeskjedResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Error {
        abstract val feilmelding: String

        @JsonTypeName("UgyldigMerkelapp")
        data class UgyldigMerkelapp(
            override val feilmelding: String
        ) : Error(), NyBeskjedResultat

        @JsonTypeName("UgyldigMottaker")
        data class UgyldigMottaker(
            override val feilmelding: String
        ) : Error(), NyBeskjedResultat
    }

    fun newGraphQL(
        kafkaProducer: CoroutineProducer<KafkaKey, Hendelse> = createKafkaProducer(),
        produsentRegister: ProdusentRegister = ProdusentRegisterImpl
    ) = TypedGraphQL<Context>(
        createGraphQL("/produsent.graphqls") {
            directive("Validate", ValidateDirective)

            scalar(Scalars.ISO8601DateTime)

            resolveSubtypes<Error>()
            resolveSubtypes<NyBeskjedResultat>()

            wire("Query") {
                dataFetcher("whoami") {
                    it.getContext<Context>().produsentid
                }
            }

            wire("Mutation") {
                coDataFetcher("nyBeskjed") { env ->
                    val nyBeskjed = env.getTypedArgument<BeskjedInput>("nyBeskjed")
                    val context = env.getContext<Context>()
                    val produsentDefinisjon = produsentRegister.finn(context.produsentid)

                    if (!produsentDefinisjon.kanSendeTil(nyBeskjed.mottaker.tilDomene())) {
                        return@coDataFetcher Error.UgyldigMottaker("""
                                | Ugyldig mottaker '${nyBeskjed.mottaker}' for produsent '${produsentDefinisjon.id}'. 
                                | Gyldige mottakere er: ${produsentDefinisjon.tillatteMottakere}
                                """.trimMargin()
                        )
                    }

                    if (!produsentDefinisjon.kanSendeTil(nyBeskjed.merkelapp)) {
                        return@coDataFetcher Error.UgyldigMerkelapp("""
                                | Ugyldig merkelapp '${nyBeskjed.merkelapp}' for produsent '${produsentDefinisjon.id}'. 
                                | Gyldige merkelapper er: ${produsentDefinisjon.tillatteMerkelapper}
                                """.trimMargin()
                        )
                    }

                    val id = UUID.randomUUID()
                    log.info("mottatt ny beskjed, id: $id, beskjed: $nyBeskjed")
                    kafkaProducer.beskjedOpprettet(nyBeskjed.tilDomene(id))
                    return@coDataFetcher NyBeskjedVellykket(id)
                }
            }
        }
    )
}
