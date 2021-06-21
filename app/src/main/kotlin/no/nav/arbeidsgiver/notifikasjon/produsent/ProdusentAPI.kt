package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

object ProdusentAPI {
    private val log = logger()

    data class Context(
        val produsentid: String,
        override val coroutineScope: CoroutineScope
    ): WithCoroutineScope

    data class NyBeskjedInput(
        val mottaker: MottakerInput,
        val notifikasjon: Notifikasjon,
        val metadata: Metadata,
    ) {
        fun tilDomene(id: UUID): Hendelse.BeskjedOpprettet {
            val mottaker = mottaker.tilDomene()
            return Hendelse.BeskjedOpprettet(
                id = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottaker = mottaker,
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = when (mottaker) {
                    is NærmesteLederMottaker -> mottaker.virksomhetsnummer
                    is AltinnMottaker -> mottaker.virksomhetsnummer
                }
            )
        }
    }

    data class NyOppgaveInput(
        val mottaker: MottakerInput,
        val notifikasjon: Notifikasjon,
        val metadata: Metadata,
    ) {
        fun tilDomene(id: UUID): Hendelse.OppgaveOpprettet {
            val mottaker = mottaker.tilDomene()
            return Hendelse.OppgaveOpprettet(
                id = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottaker = mottaker,
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = when (mottaker) {
                    is NærmesteLederMottaker -> mottaker.virksomhetsnummer
                    is AltinnMottaker -> mottaker.virksomhetsnummer
                }
            )
        }
    }

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

    data class Notifikasjon(
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

    data class Metadata(
        val grupperingsid: String?,
        val eksternId: String,
        val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyBeskjedResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyOppgaveResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface OppgaveUtfoertResultat

    @JsonTypeName("OppgaveUtfoertVellykket")
    data class OppgaveUtfoertVellykket(
        val id: UUID
    ) : OppgaveUtfoertResultat

    @JsonTypeName("NyBeskjedVellykket")
    data class NyBeskjedVellykket(
        val id: UUID
    ) : NyBeskjedResultat

    @JsonTypeName("NyOppgaveVellykket")
    data class NyOppgaveVellykket(
        val id: UUID
    ) : NyOppgaveResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Error {
        abstract val feilmelding: String

        @JsonTypeName("UgyldigMerkelapp")
        data class UgyldigMerkelapp(
            override val feilmelding: String
        ) : Error(), NyBeskjedResultat, NyOppgaveResultat, OppgaveUtfoertResultat

        @JsonTypeName("UgyldigMottaker")
        data class UgyldigMottaker(
            override val feilmelding: String
        ) : Error(), NyBeskjedResultat, NyOppgaveResultat

        @JsonTypeName("NotifikasjonFinnesIkke")
        data class NotifikasjonFinnesIkke(
            override val feilmelding: String
        ) : Error(), OppgaveUtfoertResultat
    }

    fun newGraphQL(
        kafkaProducer: CoroutineProducer<KafkaKey, Hendelse> = createKafkaProducer(),
        produsentRegister: ProdusentRegister = ProdusentRegisterImpl,
        produsentModelFuture: CompletableFuture<ProdusentModel>,
    ) = TypedGraphQL<Context>(
        createGraphQL("/produsent.graphqls") {
            directive("Validate", ValidateDirective)

            scalar(Scalars.ISO8601DateTime)

            resolveSubtypes<Error>()
            resolveSubtypes<NyBeskjedResultat>()
            resolveSubtypes<NyOppgaveResultat>()
            resolveSubtypes<OppgaveUtfoertResultat>()

            wire("Query") {
                dataFetcher("whoami") {
                    it.getContext<Context>().produsentid
                }
            }

            wire("Mutation") {
                coDataFetcher<NyBeskjedResultat>("nyBeskjed") { env ->
                    val nyBeskjed = env.getTypedArgument<NyBeskjedInput>("nyBeskjed")
                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMottaker(produsent, nyBeskjed.mottaker.tilDomene())
                        ?.let { return@coDataFetcher it }
                    tilgangsstyrMerkelapp(produsent, nyBeskjed.notifikasjon.merkelapp)
                        ?.let { return@coDataFetcher it }

                    val id = UUID.randomUUID()
                    log.info("mottatt ny beskjed, id: $id, beskjed: $nyBeskjed")
                    kafkaProducer.beskjedOpprettet(nyBeskjed.tilDomene(id))
                    return@coDataFetcher NyBeskjedVellykket(id)
                }

                coDataFetcher<NyOppgaveResultat>("nyOppgave") { env ->
                    val nyOppgave = env.getTypedArgument<NyOppgaveInput>("nyOppgave")
                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMottaker(produsent, nyOppgave.mottaker.tilDomene())
                        ?.let { return@coDataFetcher it }
                    tilgangsstyrMerkelapp(produsent, nyOppgave.notifikasjon.merkelapp)
                        ?.let { return@coDataFetcher it }

                    val id = UUID.randomUUID()
                    log.info("mottatt ny oppgave, id: $id, oppgave: $nyOppgave")
                    kafkaProducer.oppgaveOpprettet(nyOppgave.tilDomene(id))
                    return@coDataFetcher NyOppgaveVellykket(id)
                }

                coDataFetcher<OppgaveUtfoertResultat>("oppgaveUtfoert") { env ->
                    val produsentModel = produsentModelFuture.await()
                    val id = env.getTypedArgument<UUID>("id")
                    val notifikasjon = produsentModel.hentNotifikasjon(id)
                        ?: return@coDataFetcher Error.NotifikasjonFinnesIkke("Oppgave med id $id finnes ikke")

                    if (notifikasjon !is ProdusentModel.Oppgave) {
                        return@coDataFetcher Error.NotifikasjonFinnesIkke("Notifikasjon med id $id er ikke en oppgave")
                    }

                    val produsent = env.hentProdusent(produsentRegister)

                   tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp)
                       ?.let { return@coDataFetcher it }

                    val utførtHendelse = Hendelse.OppgaveUtført(
                        id = id,
                        virksomhetsnummer = notifikasjon.mottaker.virksomhetsnummer
                    )

                    kafkaProducer.oppgaveUtført(utførtHendelse)

                    produsentModel.oppdaterModellEtterHendelse(utførtHendelse)

                    return@coDataFetcher OppgaveUtfoertVellykket(id)
                }
            }
        }
    )

    private fun tilgangsstyrMerkelapp(produsent: Produsent, merkelapp: Merkelapp): Error.UgyldigMerkelapp? =
        if (produsent.kanSendeTil(merkelapp))
            null
        else
            Error.UgyldigMerkelapp(
                """
                | Ugyldig merkelapp '${merkelapp}' for produsent '${produsent.id}'. 
                | Gyldige merkelapper er: ${produsent.tillatteMerkelapper}
                """.trimMargin()
            )

    private fun tilgangsstyrMottaker(produsent: Produsent, mottaker: Mottaker): Error.UgyldigMottaker? =
        if (produsent.kanSendeTil(mottaker))
            null
        else
            Error.UgyldigMottaker(
                """
                | Ugyldig mottaker '${mottaker}' for produsent '${produsent.id}'. 
                | Gyldige mottakere er: ${produsent.tillatteMottakere}
                """.trimMargin()
            )
}

fun DataFetchingEnvironment.hentProdusent(produsentRegister: ProdusentRegister): Produsent {
    val context = this.getContext<ProdusentAPI.Context>()
    return produsentRegister.finn(context.produsentid)
}
