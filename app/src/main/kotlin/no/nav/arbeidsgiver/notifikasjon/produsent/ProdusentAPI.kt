package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Merkelapp
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import java.time.OffsetDateTime
import java.util.*

object ProdusentAPI {
    private val log = logger()

    data class Context(
        val appName: String,
        val produsent: Produsent?,
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


    /* utility interface */
    sealed interface NyNotifikasjonError: NyBeskjedResultat, NyOppgaveResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Error {
        abstract val feilmelding: String

        @JsonTypeName("UgyldigMerkelapp")
        data class UgyldigMerkelapp(
            override val feilmelding: String
        ) :
            Error(),
            NyNotifikasjonError,
            NyBeskjedResultat,
            NyOppgaveResultat,
            OppgaveUtfoertResultat,
            MineNotifikasjonerResultat,
            SoftDeleteNotifikasjonResultat,
            HardDeleteNotifikasjonResultat

        @JsonTypeName("UkjentProdusent")
        data class UkjentProdusent(
            override val feilmelding: String
        ) : Error(),
            NyBeskjedResultat,
            NyOppgaveResultat,
            NyNotifikasjonError,
            OppgaveUtfoertResultat,
            MineNotifikasjonerResultat,
            SoftDeleteNotifikasjonResultat,
            HardDeleteNotifikasjonResultat

        @JsonTypeName("UgyldigMottaker")
        data class UgyldigMottaker(
            override val feilmelding: String
        ) : Error(), NyBeskjedResultat, NyOppgaveResultat, NyNotifikasjonError

        @JsonTypeName("DuplikatEksternIdOgMerkelapp")
        data class DuplikatEksternIdOgMerkelapp(
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
        produsentRepository: ProdusentRepository,
    ): TypedGraphQL<Context> {

        fun queryWhoami(env: DataFetchingEnvironment): String {
            // TODO: returner hele context objectet som struct
            return env.getContext<Context>().appName
        }

        suspend fun queryMineNotifikasjoner(env: DataFetchingEnvironment): MineNotifikasjonerResultat {
            val merkelapp = env.getArgument<String>("merkelapp")
            val grupperingsid = env.getArgument<String>("grupperingsid")
            val first = env.getArgumentOrDefault("first", 1000)
            val after = Cursor(env.getArgumentOrDefault("after", Cursor.empty().value))
            val produsent = hentProdusent(env) { error -> return error }
            tilgangsstyrMerkelapp(produsent, merkelapp) { error -> return error }
            return produsentRepository
                .finnNotifikasjoner(
                    merkelapp = merkelapp,
                    grupperingsid = grupperingsid,
                    antall = first,
                    offset = after.offset
                )
                .map(Notifikasjon::fraDomene)
                .let { Connection.create(it, env, ::NotifikasjonConnection) }
        }

        suspend fun mutationNyBeskjed(env: DataFetchingEnvironment): NyBeskjedResultat {
            val nyBeskjed = env.getTypedArgument<NyBeskjedInput>("nyBeskjed")

            tilgangsstyrNyNotifikasjon(
                env,
                nyBeskjed.mottaker.tilDomene(),
                nyBeskjed.notifikasjon.merkelapp
            ) { error ->
                return error
            }

            val id = UUID.randomUUID()
            log.info("mottatt ny beskjed, id: $id, beskjed: $nyBeskjed")
            val domeneNyBeskjed = nyBeskjed.tilDomene(id)
            val eksisterende = produsentRepository.hentNotifikasjon(
                eksternId = domeneNyBeskjed.eksternId,
                merkelapp = domeneNyBeskjed.merkelapp
            )

            return when {
                eksisterende == null -> {
                    kafkaProducer.sendHendelseMedKey(id, domeneNyBeskjed)
                    produsentRepository.oppdaterModellEtterHendelse(domeneNyBeskjed)
                    NyBeskjedVellykket(id)
                }
                eksisterende.erDuplikatAv(domeneNyBeskjed.tilProdusentModel()) -> {
                    NyBeskjedVellykket(eksisterende.id)
                }
                else -> {
                    Error.DuplikatEksternIdOgMerkelapp(
                        "notifikasjon med angitt eksternId og merkelapp finnes fra før"
                    )
                }
            }
        }

        suspend fun mutationNyOppgave(env: DataFetchingEnvironment): NyOppgaveResultat {
            val nyOppgave = env.getTypedArgument<NyOppgaveInput>("nyOppgave")
            tilgangsstyrNyNotifikasjon(
                env,
                nyOppgave.mottaker.tilDomene(),
                nyOppgave.notifikasjon.merkelapp
            ) { error -> return error }

            val id = UUID.randomUUID()
            log.info("mottatt ny oppgave, id: $id, oppgave: $nyOppgave")
            val domeneNyOppgave = nyOppgave.tilDomene(id)
            val eksisterende = produsentRepository.hentNotifikasjon(
                eksternId = domeneNyOppgave.eksternId,
                merkelapp = domeneNyOppgave.merkelapp
            )

            return when {
                eksisterende == null -> {
                    kafkaProducer.sendHendelseMedKey(id, domeneNyOppgave)
                    produsentRepository.oppdaterModellEtterHendelse(domeneNyOppgave)
                    NyOppgaveVellykket(id)
                }
                eksisterende.erDuplikatAv(domeneNyOppgave.tilProdusentModel()) -> {
                    NyOppgaveVellykket(eksisterende.id)
                }
                else -> {
                    Error.DuplikatEksternIdOgMerkelapp(
                        "notifikasjon med angitt eksternId og merkelapp finnes fra før"
                    )
                }
            }
        }

        suspend fun mutationOppgaveUtført(env: DataFetchingEnvironment): OppgaveUtfoertResultat {
            val id = env.getTypedArgument<UUID>("id")
            val notifikasjon = produsentRepository.hentNotifikasjon(id)
                ?: return Error.NotifikasjonFinnesIkke("Oppgave med id $id finnes ikke")

            if (notifikasjon !is ProdusentModel.Oppgave) {
                return Error.NotifikasjonFinnesIkke("Notifikasjon med id $id er ikke en oppgave")
            }

            val produsent = hentProdusent(env) { error -> return error }

            tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

            val utførtHendelse = Hendelse.OppgaveUtført(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = id,
                virksomhetsnummer = notifikasjon.mottaker.virksomhetsnummer
            )

            kafkaProducer.sendHendelse(utførtHendelse)
            produsentRepository.oppdaterModellEtterHendelse(utførtHendelse)
            return OppgaveUtfoertVellykket(id)
        }

        suspend fun mutationOppgaveUtførtByEksternId(env: DataFetchingEnvironment): OppgaveUtfoertResultat {
            val eksternId = env.getTypedArgument<String>("eksternId")
            val merkelapp = env.getTypedArgument<String>("merkelapp")
            val notifikasjon = produsentRepository.hentNotifikasjon(eksternId, merkelapp)
                ?: return Error.NotifikasjonFinnesIkke("Oppgave med eksternId $eksternId og merkelapp $merkelapp finnes ikke")

            if (notifikasjon !is ProdusentModel.Oppgave) {
                return Error.NotifikasjonFinnesIkke("Notifikasjon med eksternId $eksternId og merkelapp $merkelapp er ikke en oppgave")
            }

            val produsent = hentProdusent(env) { error -> return error }

            tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

            val utførtHendelse = Hendelse.OppgaveUtført(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = notifikasjon.id,
                virksomhetsnummer = notifikasjon.mottaker.virksomhetsnummer
            )

            kafkaProducer.sendHendelse(utførtHendelse)
            produsentRepository.oppdaterModellEtterHendelse(utførtHendelse)
            return OppgaveUtfoertVellykket(notifikasjon.id)
        }

        suspend fun mutationSoftDelete(env: DataFetchingEnvironment): SoftDeleteNotifikasjonResultat {
            val id = env.getTypedArgument<UUID>("id")
            val notifikasjon = produsentRepository.hentNotifikasjon(id)
                ?: return Error.NotifikasjonFinnesIkke("Notifikasjon med id $id finnes ikke")

            val produsent = hentProdusent(env) { error -> return error }
            tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

            val softDelete = Hendelse.SoftDelete(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = id,
                virksomhetsnummer = notifikasjon.virksomhetsnummer,
                deletedAt = OffsetDateTime.now()
            )

            kafkaProducer.sendHendelse(softDelete)
            produsentRepository.oppdaterModellEtterHendelse(softDelete)
            return SoftDeleteNotifikasjonVellykket(id)
        }

        suspend fun mutationSoftDeleteNotifikasjonByEksternId(env: DataFetchingEnvironment): SoftDeleteNotifikasjonResultat {
            val eksternId = env.getTypedArgument<String>("eksternId")
            val merkelapp = env.getTypedArgument<String>("merkelapp")
            val notifikasjon = produsentRepository.hentNotifikasjon(eksternId, merkelapp)
                ?: return Error.NotifikasjonFinnesIkke("Notifikasjon med eksternId $eksternId og merkelapp $merkelapp finnes ikke")

            val produsent = hentProdusent(env) { error ->
                return error
            }

            tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error ->
                return error
            }

            val softDelete = Hendelse.SoftDelete(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = notifikasjon.id,
                virksomhetsnummer = notifikasjon.virksomhetsnummer,
                deletedAt = OffsetDateTime.now()
            )

            kafkaProducer.sendHendelse(softDelete)
            produsentRepository.oppdaterModellEtterHendelse(softDelete)
            return SoftDeleteNotifikasjonVellykket(notifikasjon.id)
        }

        suspend fun mutationHardDeleteNotifikasjon(env: DataFetchingEnvironment): HardDeleteNotifikasjonResultat {
            val id = env.getTypedArgument<UUID>("id")
            val notifikasjon = produsentRepository.hentNotifikasjon(id)
                ?: return Error.NotifikasjonFinnesIkke("Notifikasjon med id $id finnes ikke")

            val produsent = hentProdusent(env) { error -> return error }
            tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

            val hardDelete = Hendelse.HardDelete(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = id,
                virksomhetsnummer = notifikasjon.virksomhetsnummer,
                deletedAt = OffsetDateTime.now()
            )

            kafkaProducer.sendHendelse(hardDelete)
            produsentRepository.oppdaterModellEtterHendelse(hardDelete)
            return HardDeleteNotifikasjonVellykket(id)
        }

        suspend fun mutationHardDeleteNotifikasjonByEksternId(env: DataFetchingEnvironment): HardDeleteNotifikasjonResultat {
            val eksternId = env.getTypedArgument<String>("eksternId")
            val merkelapp = env.getTypedArgument<String>("merkelapp")
            val notifikasjon = produsentRepository.hentNotifikasjon(eksternId, merkelapp)
                ?: return Error.NotifikasjonFinnesIkke("Notifikasjon med eksternId $eksternId og merkelapp $merkelapp finnes ikke")

            val produsent = hentProdusent(env) { error -> return error }

            tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

            val hardDelete = Hendelse.HardDelete(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = notifikasjon.id,
                virksomhetsnummer = notifikasjon.virksomhetsnummer,
                deletedAt = OffsetDateTime.now()
            )

            kafkaProducer.sendHendelse(hardDelete)
            produsentRepository.oppdaterModellEtterHendelse(hardDelete)
            return HardDeleteNotifikasjonVellykket(notifikasjon.id)
        }

        return TypedGraphQL(
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
                    dataFetcher("whoami", ::queryWhoami)
                    coDataFetcher("mineNotifikasjoner", ::queryMineNotifikasjoner)
                }

                wire("Mutation") {
                    coDataFetcher("nyBeskjed", ::mutationNyBeskjed)
                    coDataFetcher("nyOppgave", ::mutationNyOppgave)
                    coDataFetcher("oppgaveUtfoert", ::mutationOppgaveUtført)
                    coDataFetcher("oppgaveUtfoertByEksternId", ::mutationOppgaveUtførtByEksternId)
                    coDataFetcher("softDeleteNotifikasjon", ::mutationSoftDelete)
                    coDataFetcher("softDeleteNotifikasjonByEksternId", ::mutationSoftDeleteNotifikasjonByEksternId)
                    coDataFetcher("hardDeleteNotifikasjon", ::mutationHardDeleteNotifikasjon)
                    coDataFetcher("hardDeleteNotifikasjonByEksternId", ::mutationHardDeleteNotifikasjonByEksternId)
                }
            }
        )
    }
}

private inline fun tilgangsstyrNyNotifikasjon(
    env: DataFetchingEnvironment,
    mottaker: Mottaker,
    merkelapp: String,
    onError: (ProdusentAPI.NyNotifikasjonError) -> Nothing
) {
    val produsent = hentProdusent(env) { error -> onError(error) }
    tilgangsstyrMottaker(produsent, mottaker) { error -> onError(error) }
    tilgangsstyrMerkelapp(produsent, merkelapp) { error -> onError(error) }
}

private inline fun tilgangsstyrMerkelapp(
    produsent: Produsent,
    merkelapp: Merkelapp,
    onError: (error: ProdusentAPI.Error.UgyldigMerkelapp) -> Nothing
) {
    if (!produsent.kanSendeTil(merkelapp)) {
        onError(
            ProdusentAPI.Error.UgyldigMerkelapp(
                """
                    | Ugyldig merkelapp '${merkelapp}'.
                    | Gyldige merkelapper er: ${produsent.tillatteMerkelapper}
                    """.trimMargin()
            )
        )
    }
}

private inline fun tilgangsstyrMottaker(
    produsent: Produsent,
    mottaker: Mottaker,
    onError: (error: ProdusentAPI.Error.UgyldigMottaker) -> Nothing
) {
    if (!produsent.kanSendeTil(mottaker)) {
        onError(
            ProdusentAPI.Error.UgyldigMottaker(
                """
                    | Ugyldig mottaker '${mottaker}'. 
                    | Gyldige mottakere er: ${produsent.tillatteMottakere}
                    """.trimMargin()
            )
        )
    }
}

inline fun hentProdusent(
    env: DataFetchingEnvironment,
    onMissing: (error: ProdusentAPI.Error.UkjentProdusent) -> Nothing
): Produsent {
    val context = env.getContext<ProdusentAPI.Context>()
    if (context.produsent == null) {
        onMissing(ProdusentAPI.Error.UkjentProdusent(
            "Finner ikke produsent med id ${context.appName}"
        ))
    } else {
        return context.produsent
    }
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