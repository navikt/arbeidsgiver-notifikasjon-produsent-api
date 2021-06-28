package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.annotation.JsonValue
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import java.util.*
import java.util.Base64.getDecoder
import java.util.Base64.getEncoder
import java.util.concurrent.CompletableFuture

object ProdusentAPI {
    private val log = logger()

    data class Context(
        val produsentid: String,
        override val coroutineScope: CoroutineScope
    ) : WithCoroutineScope

    data class NyBeskjedInput(
        val mottaker: MottakerInput,
        val notifikasjon: NotifikasjonData,
        val metadata: MetadataInput,
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
                    is no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker -> mottaker.virksomhetsnummer
                    is no.nav.arbeidsgiver.notifikasjon.AltinnMottaker -> mottaker.virksomhetsnummer
                }
            )
        }
    }

    data class NyOppgaveInput(
        val mottaker: MottakerInput,
        val notifikasjon: NotifikasjonData,
        val metadata: MetadataInput,
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
                    is no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker -> mottaker.virksomhetsnummer
                    is no.nav.arbeidsgiver.notifikasjon.AltinnMottaker -> mottaker.virksomhetsnummer
                }
            )
        }
    }

    data class NaermesteLederMottakerInput(
        val naermesteLederFnr: String,
        val ansattFnr: String,
        val virksomhetsnummer: String
    ) {
        fun tilDomene(): no.nav.arbeidsgiver.notifikasjon.Mottaker =
            no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker(
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
        fun tilDomene(): no.nav.arbeidsgiver.notifikasjon.Mottaker =
            no.nav.arbeidsgiver.notifikasjon.AltinnMottaker(
                serviceCode = serviceCode,
                serviceEdition = serviceEdition,
                virksomhetsnummer = virksomhetsnummer
            )
    }

    data class MottakerInput(
        val altinn: AltinnMottakerInput?,
        val naermesteLeder: NaermesteLederMottakerInput?
    ) {

        fun tilDomene(): no.nav.arbeidsgiver.notifikasjon.Mottaker {
            return if (altinn != null && naermesteLeder == null) {
                altinn.tilDomene()
            } else if (naermesteLeder != null && altinn == null) {
                naermesteLeder.tilDomene()
            } else {
                throw IllegalArgumentException("Ugyldig mottaker")
            }
        }
    }

    data class NotifikasjonData(
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

    data class MetadataInput(
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

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface MineNotifikasjonerResultat

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
        ) : Error(), NyBeskjedResultat, NyOppgaveResultat, OppgaveUtfoertResultat, MineNotifikasjonerResultat

        @JsonTypeName("UgyldigMottaker")
        data class UgyldigMottaker(
            override val feilmelding: String
        ) : Error(), NyBeskjedResultat, NyOppgaveResultat

        @JsonTypeName("NotifikasjonFinnesIkke")
        data class NotifikasjonFinnesIkke(
            override val feilmelding: String
        ) : Error(), OppgaveUtfoertResultat
    }

    data class Cursor(
        @get:JsonValue val value: String
    ) {
        operator fun compareTo(other: Cursor): Int = offset.compareTo(other.offset)
        operator fun plus(increment: Int): Cursor = of(offset + increment)
        override fun toString(): String = value

        val next: Cursor
            get() = this + 1

        private val offset: Int
            get() = Integer.parseInt(
                String(
                    getDecoder().decode(value)
                ).replace(PREFIX, "")
            )

        companion object {
            const val PREFIX = "cur"

            fun of(offset: Int) : Cursor {
                return Cursor(
                    getEncoder().encodeToString(
                        "$PREFIX$offset".toByteArray()
                    )
                )
            }

            fun empty() : Cursor {
                return of(0)
            }
        }
    }

    open class Connection<T>(
        open val edges: List<Edge<T>>,
        open val pageInfo: PageInfo
    ) {
        companion object {
            private fun <T> empty() : Connection<T> {
                return Connection(emptyList(), PageInfo(Cursor.empty(), false))
            }
            fun <T> create(data: List<T>, env: DataFetchingEnvironment) : Connection<T> {
                if (data.isEmpty()) {
                    return empty()
                }

                val first = env.getArgumentOrDefault("first", data.size)
                val after = Cursor(env.getArgumentOrDefault("after", Cursor.empty().value))
                val cursorSeq = generateSequence(after) { after + 1 }.iterator()
                val edges = data.map {
                    Edge(cursorSeq.next(), it)
                }.subList(0, first)
                val pageInfo = PageInfo(
                    edges.last().cursor,
                    edges.last().cursor >= after.plus(first)
                )

                return Connection(edges, pageInfo)
            }
        }
    }

    @JsonTypeName("NotifikasjonConnection")
    data class NotifikasjonConnection(
        override val edges: List<Edge<Notifikasjon>>,
        override val pageInfo: PageInfo
    ) : MineNotifikasjonerResultat, Connection<Notifikasjon>(edges, pageInfo)

    @JsonTypeName("PageInfo")
    data class PageInfo(
        val endCursor: Cursor,
        val hasNextPage: Boolean
    )

    @JsonTypeName("Edge")
    data class Edge<T>(
        val cursor: Cursor,
        val node: T
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Notifikasjon {
        @JsonTypeName("Beskjed")
        data class Beskjed(
            val mottaker: Mottaker,
            val metadata: Metadata,
            val beskjed: BeskjedData,
        ) : Notifikasjon()

        @JsonTypeName("Oppgave")
        data class Oppgave(
            val mottaker: Mottaker,
            val metadata: Metadata,
            val oppgave: OppgaveData,
        ) : Notifikasjon() {
            enum class Tilstand {
                NY,
                UTFOERT;
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Mottaker {
        companion object {
            fun fraDomene(domene: no.nav.arbeidsgiver.notifikasjon.Mottaker): Mottaker {
                return when (domene) {
                    is no.nav.arbeidsgiver.notifikasjon.AltinnMottaker -> AltinnMottaker(
                        serviceCode = domene.serviceCode,
                        serviceEdition = domene.serviceEdition,
                        virksomhetsnummer = domene.virksomhetsnummer
                    )
                    is no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker -> NærmesteLederMottaker(
                        naermesteLederFnr = domene.naermesteLederFnr,
                        ansattFnr = domene.ansattFnr,
                        virksomhetsnummer = domene.virksomhetsnummer,
                    )
                }
            }
        }
    }

    @JsonTypeName("NaermesteLederMottaker")
    data class NærmesteLederMottaker(
        val naermesteLederFnr: String,
        val ansattFnr: String,
        val virksomhetsnummer: String
    ) : Mottaker()

    @JsonTypeName("AltinnMottaker")
    data class AltinnMottaker(
        val serviceCode: String,
        val serviceEdition: String,
        val virksomhetsnummer: String,
    ) : Mottaker()

    @JsonTypeName("Metadata")
    data class Metadata(
        val id: UUID,
        val grupperingsid: String?,
        val eksternId: String,
        val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    )

    @JsonTypeName("OppgaveData")
    data class OppgaveData(
        val tilstand: Notifikasjon.Oppgave.Tilstand,
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

    @JsonTypeName("BeskjedData")
    data class BeskjedData(
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

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
            resolveSubtypes<MineNotifikasjonerResultat>()
            resolveSubtypes<Notifikasjon>()
            resolveSubtypes<Mottaker>()

            wire("Query") {
                dataFetcher("whoami") {
                    it.getContext<Context>().produsentid
                }

                coDataFetcher("mineNotifikasjoner") { env ->
                    val merkelapp = env.getArgument<String>("merkelapp")
                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMerkelapp(produsent, merkelapp)?.let { return@coDataFetcher it }

                    return@coDataFetcher produsentModelFuture.await()
                        .finnNotifikasjoner(merkelapp)
                        .map { notifikasjon ->
                            when (notifikasjon) {
                                is ProdusentModel.Beskjed ->
                                    Notifikasjon.Beskjed(
                                        metadata = Metadata(
                                            id = notifikasjon.id,
                                            grupperingsid = notifikasjon.grupperingsid,
                                            eksternId = notifikasjon.eksternId,
                                            opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                                        ),
                                        mottaker = Mottaker.fraDomene(notifikasjon.mottaker),
                                        beskjed = BeskjedData(
                                            merkelapp = notifikasjon.merkelapp,
                                            tekst = notifikasjon.tekst,
                                            lenke = notifikasjon.lenke,
                                        )
                                    )
                                is ProdusentModel.Oppgave ->
                                    Notifikasjon.Oppgave(
                                        metadata = Metadata(
                                            id = notifikasjon.id,
                                            grupperingsid = notifikasjon.grupperingsid,
                                            eksternId = notifikasjon.eksternId,
                                            opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                                        ),
                                        mottaker = Mottaker.fraDomene(notifikasjon.mottaker),
                                        oppgave = OppgaveData(
                                            tilstand = enumValueOf(notifikasjon.tilstand.name),
                                            merkelapp = notifikasjon.merkelapp,
                                            tekst = notifikasjon.tekst,
                                            lenke = notifikasjon.lenke,
                                        )
                                    )
                            }
                        }.let {
                            val connection = Connection.create(it, env)
                            NotifikasjonConnection(connection.edges, connection.pageInfo)
                        }
                }
            }

            wire("Mutation") {
                coDataFetcher("nyBeskjed") { env ->
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

                coDataFetcher("nyOppgave") { env ->
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

                coDataFetcher("oppgaveUtfoert") { env ->
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

                coDataFetcher("oppgaveUtfoertByEksternId") { env ->
                    val produsentModel = produsentModelFuture.await()
                    val eksternId = env.getTypedArgument<String>("eksternId")
                    val merkelapp = env.getTypedArgument<String>("merkelapp")
                    val notifikasjon = produsentModel.hentNotifikasjon(eksternId, merkelapp)
                        ?: return@coDataFetcher Error.NotifikasjonFinnesIkke("Oppgave med eksternId $eksternId og merkelapp $merkelapp finnes ikke")

                    if (notifikasjon !is ProdusentModel.Oppgave) {
                        return@coDataFetcher Error.NotifikasjonFinnesIkke("Notifikasjon med eksternId $eksternId og merkelapp $merkelapp er ikke en oppgave")
                    }

                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp)
                        ?.let { return@coDataFetcher it }

                    val utførtHendelse = Hendelse.OppgaveUtført(
                        id = notifikasjon.id,
                        virksomhetsnummer = notifikasjon.mottaker.virksomhetsnummer
                    )

                    kafkaProducer.oppgaveUtført(utførtHendelse)
                    produsentModel.oppdaterModellEtterHendelse(utførtHendelse)
                    return@coDataFetcher OppgaveUtfoertVellykket(notifikasjon.id)
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

    private fun tilgangsstyrMottaker(produsent: Produsent, mottaker: no.nav.arbeidsgiver.notifikasjon.Mottaker): Error.UgyldigMottaker? =
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
