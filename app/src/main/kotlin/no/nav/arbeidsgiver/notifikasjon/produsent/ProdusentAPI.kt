package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import java.time.OffsetDateTime
import java.util.*

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
                hendelseId = id,
                notifikasjonId = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottaker = mottaker,
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = mottaker.virksomhetsnummer,
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
                hendelseId = id,
                notifikasjonId = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottaker = mottaker,
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = mottaker.virksomhetsnummer
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

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface SoftDeleteNotifikasjonResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HardDeleteNotifikasjonResultat

    @JsonTypeName("OppgaveUtfoertVellykket")
    data class OppgaveUtfoertVellykket(
        val id: UUID
    ) : OppgaveUtfoertResultat

    @JsonTypeName("SoftDeleteNotifikasjonVellykket")
    data class SoftDeleteNotifikasjonVellykket(
        val id: UUID
    ) : SoftDeleteNotifikasjonResultat

    @JsonTypeName("HardDeleteNotifikasjonVellykket")
    data class HardDeleteNotifikasjonVellykket(
        val id: UUID
    ) : HardDeleteNotifikasjonResultat

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
        ) :
            Error(),
            NyBeskjedResultat,
            NyOppgaveResultat,
            OppgaveUtfoertResultat,
            MineNotifikasjonerResultat,
            SoftDeleteNotifikasjonResultat,
            HardDeleteNotifikasjonResultat

        @JsonTypeName("UgyldigMottaker")
        data class UgyldigMottaker(
            override val feilmelding: String
        ) : Error(), NyBeskjedResultat, NyOppgaveResultat

        @JsonTypeName("NotifikasjonFinnesIkke")
        data class NotifikasjonFinnesIkke(
            override val feilmelding: String
        ) :
            Error(),
            OppgaveUtfoertResultat,
            SoftDeleteNotifikasjonResultat,
            HardDeleteNotifikasjonResultat
    }


    @JsonTypeName("NotifikasjonConnection")
    data class NotifikasjonConnection(
        val edges: List<Edge<Notifikasjon>>,
        val pageInfo: PageInfo
    ) : MineNotifikasjonerResultat, Connection<Notifikasjon>

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Notifikasjon {
        @JsonTypeName("Beskjed")
        data class Beskjed(
            val mottaker: Mottaker,
            val metadata: Metadata,
            val beskjed: BeskjedData,
        ) : Notifikasjon() {
            companion object {
                fun fraDomene(beskjed: ProdusentModel.Beskjed): Beskjed {
                    return Beskjed(
                        metadata = Metadata(
                            id = beskjed.id,
                            grupperingsid = beskjed.grupperingsid,
                            eksternId = beskjed.eksternId,
                            opprettetTidspunkt = beskjed.opprettetTidspunkt,
                            softDeletedAt = beskjed.deletedAt,
                        ),
                        mottaker = Mottaker.fraDomene(beskjed.mottaker),
                        beskjed = BeskjedData(
                            merkelapp = beskjed.merkelapp,
                            tekst = beskjed.tekst,
                            lenke = beskjed.lenke,
                        )
                    )
                }
            }
        }

        @JsonTypeName("Oppgave")
        data class Oppgave(
            val mottaker: Mottaker,
            val metadata: Metadata,
            val oppgave: OppgaveData,
        ) : Notifikasjon() {
            @Suppress("unused")
            /* Sendes til produsent */
            enum class Tilstand {
                NY,
                UTFOERT;
            }

            companion object {
                fun fraDomene(oppgave: ProdusentModel.Oppgave): Oppgave {
                    return Oppgave(
                        metadata = Metadata(
                            id = oppgave.id,
                            grupperingsid = oppgave.grupperingsid,
                            eksternId = oppgave.eksternId,
                            opprettetTidspunkt = oppgave.opprettetTidspunkt,
                            softDeletedAt = oppgave.deletedAt
                        ),
                        mottaker = Mottaker.fraDomene(oppgave.mottaker),
                        oppgave = OppgaveData(
                            tilstand = enumValueOf(oppgave.tilstand.name),
                            merkelapp = oppgave.merkelapp,
                            tekst = oppgave.tekst,
                            lenke = oppgave.lenke,
                        )
                    )
                }
            }
        }

        companion object {
            fun fraDomene(notifikasjon: ProdusentModel.Notifikasjon): Notifikasjon {
                return when (notifikasjon) {
                    is ProdusentModel.Beskjed -> Beskjed.fraDomene(notifikasjon)
                    is ProdusentModel.Oppgave -> Oppgave.fraDomene(notifikasjon)
                }
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Mottaker {
        companion object {
            fun fraDomene(domene: no.nav.arbeidsgiver.notifikasjon.Mottaker): Mottaker {
                return when (domene) {
                    is no.nav.arbeidsgiver.notifikasjon.AltinnMottaker -> AltinnMottaker.fraDomene(domene)
                    is no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker -> NærmesteLederMottaker.fraDomene(domene)
                }
            }
        }
    }

    @JsonTypeName("NaermesteLederMottaker")
    data class NærmesteLederMottaker(
        val naermesteLederFnr: String,
        val ansattFnr: String,
        val virksomhetsnummer: String
    ) : Mottaker() {
        companion object {
            fun fraDomene(domene: no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker): NærmesteLederMottaker {
                return NærmesteLederMottaker(
                    naermesteLederFnr = domene.naermesteLederFnr,
                    ansattFnr = domene.ansattFnr,
                    virksomhetsnummer = domene.virksomhetsnummer,
                )
            }
        }
    }

    @JsonTypeName("AltinnMottaker")
    data class AltinnMottaker(
        val serviceCode: String,
        val serviceEdition: String,
        val virksomhetsnummer: String,
    ) : Mottaker() {
        companion object {
            fun fraDomene(domene: no.nav.arbeidsgiver.notifikasjon.AltinnMottaker): AltinnMottaker {
                return AltinnMottaker(
                    serviceCode = domene.serviceCode,
                    serviceEdition = domene.serviceEdition,
                    virksomhetsnummer = domene.virksomhetsnummer
                )
            }
        }
    }

    @JsonTypeName("Metadata")
    data class Metadata(
        val id: UUID,
        val grupperingsid: String?,
        val eksternId: String,
        val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
        val softDeletedAt: OffsetDateTime?,
    ) {
        val softDeleted: Boolean
            get() = softDeletedAt != null
    }

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
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse> = createKafkaProducer(),
        produsentRegister: ProdusentRegister,
        produsentModel: ProdusentModel,
    ) = TypedGraphQL<Context>(
        createGraphQL("/produsent.graphql") {
            directive("Validate", ValidateDirective)

            scalar(Scalars.ISO8601DateTime)

            resolveSubtypes<Error>()
            resolveSubtypes<Notifikasjon>()
            resolveSubtypes<Mottaker>()

            resolveSubtypes<NyBeskjedResultat>()
            resolveSubtypes<NyOppgaveResultat>()
            resolveSubtypes<OppgaveUtfoertResultat>()
            resolveSubtypes<MineNotifikasjonerResultat>()
            resolveSubtypes<SoftDeleteNotifikasjonResultat>()
            resolveSubtypes<HardDeleteNotifikasjonResultat>()

            wire("Query") {
                dataFetcher("whoami") {
                    it.getContext<Context>().produsentid
                }

                coDataFetcher("mineNotifikasjoner") { env ->
                    val merkelapp = env.getArgument<String>("merkelapp")
                    val first = env.getArgumentOrDefault("first", 1000)
                    val after = Cursor(env.getArgumentOrDefault("after", Cursor.empty().value))
                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMerkelapp(produsent, merkelapp)?.let { return@coDataFetcher it }

                    return@coDataFetcher produsentModel
                        .finnNotifikasjoner(merkelapp = merkelapp, antall = first, offset = after.offset)
                        .map(Notifikasjon::fraDomene)
                        .let { Connection.create(it, env, ::NotifikasjonConnection) }
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
                    val domeneNyBeskjed = nyBeskjed.tilDomene(id)
                    kafkaProducer.sendHendelseMedKey(id, domeneNyBeskjed)
                    produsentModel.oppdaterModellEtterHendelse(domeneNyBeskjed)
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
                    val nyOppgaveDomene = nyOppgave.tilDomene(id)
                    kafkaProducer.sendHendelseMedKey(id, nyOppgaveDomene)
                    produsentModel.oppdaterModellEtterHendelse(nyOppgaveDomene)
                    return@coDataFetcher NyOppgaveVellykket(id)
                }

                coDataFetcher("oppgaveUtfoert") { env ->
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
                        hendelseId = UUID.randomUUID(),
                        notifikasjonId = id,
                        virksomhetsnummer = notifikasjon.mottaker.virksomhetsnummer
                    )

                    kafkaProducer.sendHendelse(utførtHendelse)
                    produsentModel.oppdaterModellEtterHendelse(utførtHendelse)
                    return@coDataFetcher OppgaveUtfoertVellykket(id)
                }

                coDataFetcher("oppgaveUtfoertByEksternId") { env ->
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
                        hendelseId = UUID.randomUUID(),
                        notifikasjonId = notifikasjon.id,
                        virksomhetsnummer = notifikasjon.mottaker.virksomhetsnummer
                    )

                    kafkaProducer.sendHendelse(utførtHendelse)
                    produsentModel.oppdaterModellEtterHendelse(utførtHendelse)
                    return@coDataFetcher OppgaveUtfoertVellykket(notifikasjon.id)
                }

                coDataFetcher("softDeleteNotifikasjon") { env ->
                    val id = env.getTypedArgument<UUID>("id")
                    val notifikasjon = produsentModel.hentNotifikasjon(id)
                        ?: return@coDataFetcher Error.NotifikasjonFinnesIkke("Notifikasjon med id $id finnes ikke")

                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp)
                        ?.let { return@coDataFetcher it }

                    val softDelete = Hendelse.SoftDelete(
                        hendelseId = UUID.randomUUID(),
                        notifikasjonId = id,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        deletedAt = OffsetDateTime.now()
                    )

                    kafkaProducer.sendHendelse(softDelete)
                    produsentModel.oppdaterModellEtterHendelse(softDelete)
                    return@coDataFetcher SoftDeleteNotifikasjonVellykket(id)
                }

                coDataFetcher("softDeleteNotifikasjonByEksternId") { env ->
                    val eksternId = env.getTypedArgument<String>("eksternId")
                    val merkelapp = env.getTypedArgument<String>("merkelapp")
                    val notifikasjon = produsentModel.hentNotifikasjon(eksternId, merkelapp)
                        ?: return@coDataFetcher Error.NotifikasjonFinnesIkke("Notifikasjon med eksternId $eksternId og merkelapp $merkelapp finnes ikke")

                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp)
                        ?.let { return@coDataFetcher it }

                    val softDelete = Hendelse.SoftDelete(
                        hendelseId = UUID.randomUUID(),
                        notifikasjonId = notifikasjon.id,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        deletedAt = OffsetDateTime.now()
                    )

                    kafkaProducer.sendHendelse(softDelete)
                    produsentModel.oppdaterModellEtterHendelse(softDelete)
                    return@coDataFetcher SoftDeleteNotifikasjonVellykket(notifikasjon.id)
                }

                coDataFetcher("hardDeleteNotifikasjon") { env ->
                    val id = env.getTypedArgument<UUID>("id")
                    val notifikasjon = produsentModel.hentNotifikasjon(id)
                        ?: return@coDataFetcher Error.NotifikasjonFinnesIkke("Notifikasjon med id $id finnes ikke")

                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp)
                        ?.let { return@coDataFetcher it }

                    val hardDelete = Hendelse.HardDelete(
                        hendelseId = UUID.randomUUID(),
                        notifikasjonId = id,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        deletedAt = OffsetDateTime.now()
                    )

                    kafkaProducer.sendHendelse(hardDelete)
                    produsentModel.oppdaterModellEtterHendelse(hardDelete)
                    return@coDataFetcher HardDeleteNotifikasjonVellykket(id)
                }

                coDataFetcher("hardDeleteNotifikasjonByEksternId") { env ->
                    val eksternId = env.getTypedArgument<String>("eksternId")
                    val merkelapp = env.getTypedArgument<String>("merkelapp")
                    val notifikasjon = produsentModel.hentNotifikasjon(eksternId, merkelapp)
                        ?: return@coDataFetcher Error.NotifikasjonFinnesIkke("Notifikasjon med eksternId $eksternId og merkelapp $merkelapp finnes ikke")

                    val produsent = env.hentProdusent(produsentRegister)

                    tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp)
                        ?.let { return@coDataFetcher it }

                    val hardDelete = Hendelse.HardDelete(
                        hendelseId = UUID.randomUUID(),
                        notifikasjonId = notifikasjon.id,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        deletedAt = OffsetDateTime.now()
                    )

                    kafkaProducer.sendHendelse(hardDelete)
                    produsentModel.oppdaterModellEtterHendelse(hardDelete)
                    return@coDataFetcher HardDeleteNotifikasjonVellykket(notifikasjon.id)
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
                | Ugyldig merkelapp '${merkelapp}'.
                | Gyldige merkelapper er: ${produsent.tillatteMerkelapper}
                """.trimMargin()
            )

    private fun tilgangsstyrMottaker(
        produsent: Produsent,
        mottaker: no.nav.arbeidsgiver.notifikasjon.Mottaker
    ): Error.UgyldigMottaker? =
        if (produsent.kanSendeTil(mottaker))
            null
        else
            Error.UgyldigMottaker(
                """
                | Ugyldig mottaker '${mottaker}'. 
                | Gyldige mottakere er: ${produsent.tillatteMottakere}
                """.trimMargin()
            )
}

fun DataFetchingEnvironment.hentProdusent(produsentRegister: ProdusentRegister): Produsent {
    val context = this.getContext<ProdusentAPI.Context>()
    return produsentRegister.finn(context.produsentid)
}

val ProdusentModel.Notifikasjon.virksomhetsnummer: String
    get() = when (this) {
        is ProdusentModel.Oppgave -> this.mottaker.virksomhetsnummer
        is ProdusentModel.Beskjed -> this.mottaker.virksomhetsnummer
    }

val ProdusentAPI.Notifikasjon.metadata: ProdusentAPI.Metadata
    get() = when (this) {
        is ProdusentAPI.Notifikasjon.Beskjed -> this.metadata
        is ProdusentAPI.Notifikasjon.Oppgave -> this.metadata
    }

val ProdusentAPI.Notifikasjon.id: UUID
    get() = this.metadata.id