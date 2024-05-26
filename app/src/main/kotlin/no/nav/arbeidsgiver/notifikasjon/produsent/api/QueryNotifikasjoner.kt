package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

internal class QueryNotifikasjoner(
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<MineNotifikasjonerResultat>()
        runtime.resolveSubtypes<HentNotifikasjonResultat>()
        runtime.resolveSubtypes<Notifikasjon>()
        runtime.resolveSubtypes<Mottaker>()

        runtime.wire("Query") {
            coDataFetcher("mineNotifikasjoner") { env ->
                mineNotifikasjoner(
                    context = env.notifikasjonContext(),
                    inputMerkelapp = env.getArgument<String?>("merkelapp"),
                    inputMerkelapper = env.getArgument<List<String>?>("merkelapper"),
                    grupperingsid = env.getArgument<String>("grupperingsid"),
                    first = env.getArgumentOrDefault("first", 1000),
                    after = Cursor(env.getArgumentOrDefault("after", Cursor.empty().value)),
                    env,
                )
            }
            coDataFetcher("hentNotifikasjon") { env ->
                hentNotifikasjon(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id"),
                )
            }
        }
    }

    @JsonTypeName("NotifikasjonConnection")
    data class NotifikasjonConnection(
        val edges: List<Edge<Notifikasjon>>,
        val pageInfo: PageInfo
    ) :
        MineNotifikasjonerResultat,
        Connection<Notifikasjon>

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Notifikasjon {
        @JsonTypeName("Beskjed")
        data class Beskjed(
            val mottakere: List<Mottaker>,
            val mottaker: Mottaker,
            val metadata: Metadata,
            val beskjed: BeskjedData,
            val eksterneVarsler: List<EksterntVarsel>,
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
                        mottaker = Mottaker.fraDomene(beskjed.mottakere.first()),
                        mottakere = beskjed.mottakere.map { Mottaker.fraDomene(it) },
                        beskjed = BeskjedData(
                            merkelapp = beskjed.merkelapp,
                            tekst = beskjed.tekst,
                            lenke = beskjed.lenke,
                        ),
                        eksterneVarsler = beskjed.eksterneVarsler.map(EksterntVarsel.Companion::fraDomene),
                    )
                }
            }
        }

        @JsonTypeName("Oppgave")
        data class Oppgave(
            val mottaker: Mottaker,
            val mottakere: List<Mottaker>,
            val metadata: Metadata,
            val oppgave: OppgaveData,
            val eksterneVarsler: List<EksterntVarsel>,
        ) : Notifikasjon() {
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
                        mottaker = Mottaker.fraDomene(oppgave.mottakere.first()),
                        mottakere = oppgave.mottakere.map { Mottaker.fraDomene(it) },
                        oppgave = OppgaveData(
                            tilstand = enumValueOf(oppgave.tilstand.name),
                            merkelapp = oppgave.merkelapp,
                            tekst = oppgave.tekst,
                            lenke = oppgave.lenke,
                        ),
                        eksterneVarsler = oppgave.eksterneVarsler.map(EksterntVarsel.Companion::fraDomene),
                    )
                }
            }
        }

        @JsonTypeName("Kalenderavtale")
        data class Kalenderavtale(
            val mottaker: Mottaker,
            val mottakere: List<Mottaker>,
            val metadata: Metadata,
            val kalenderavtale: KalenderavtaleData,
            val eksterneVarsler: List<EksterntVarsel>,
        ) : Notifikasjon() {
            enum class Tilstand {
                VENTER_SVAR_FRA_ARBEIDSGIVER,
                ARBEIDSGIVER_VIL_AVLYSE,
                ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
                ARBEIDSGIVER_HAR_GODTATT,
                AVLYST;
            }

            companion object {
                fun fraDomene(kalenderavtale: ProdusentModel.Kalenderavtale): Kalenderavtale {
                    return Kalenderavtale(
                        metadata = Metadata(
                            id = kalenderavtale.id,
                            grupperingsid = kalenderavtale.grupperingsid,
                            eksternId = kalenderavtale.eksternId,
                            opprettetTidspunkt = kalenderavtale.opprettetTidspunkt,
                            softDeletedAt = kalenderavtale.deletedAt
                        ),
                        mottaker = Mottaker.fraDomene(kalenderavtale.mottakere.first()),
                        mottakere = kalenderavtale.mottakere.map { Mottaker.fraDomene(it) },
                        kalenderavtale = KalenderavtaleData(
                            merkelapp = kalenderavtale.merkelapp,
                            tekst = kalenderavtale.tekst,
                            lenke = kalenderavtale.lenke,
                            startTidspunkt = kalenderavtale.startTidspunkt,
                            sluttTidspunkt = kalenderavtale.sluttTidspunkt,
                            lokasjon = kalenderavtale.lokasjon?.let {
                                Lokasjon(
                                    adresse = it.adresse,
                                    postnummer = it.postnummer,
                                    poststed = it.poststed,
                                )
                            },
                            digitalt = kalenderavtale.digitalt,
                            tilstand = enumValueOf(kalenderavtale.tilstand.name),
                        ),
                        eksterneVarsler = kalenderavtale.eksterneVarsler.map(EksterntVarsel.Companion::fraDomene),
                    )
                }
            }
        }

        companion object {
            fun fraDomene(notifikasjon: ProdusentModel.Notifikasjon): Notifikasjon {
                return when (notifikasjon) {
                    is ProdusentModel.Beskjed -> Beskjed.fraDomene(notifikasjon)
                    is ProdusentModel.Oppgave -> Oppgave.fraDomene(notifikasjon)
                    is ProdusentModel.Kalenderavtale -> Kalenderavtale.fraDomene(notifikasjon)
                }
            }
        }
    }

    data class NotifikasjonData(
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

    data class EksterntVarsel(
        val id: UUID,
        val status: EksterntVarselStatus,
        val feilmelding: String?,
    ) {
        companion object {
            fun fraDomene(domene: ProdusentModel.EksterntVarsel): EksterntVarsel = EksterntVarsel(
                id = domene.varselId,
                status = when (domene.status) {
                    ProdusentModel.EksterntVarsel.Status.NY -> EksterntVarselStatus.NY
                    ProdusentModel.EksterntVarsel.Status.FEILET -> EksterntVarselStatus.FEILET
                    ProdusentModel.EksterntVarsel.Status.SENDT -> EksterntVarselStatus.SENDT
                    ProdusentModel.EksterntVarsel.Status.KANSELLERT -> EksterntVarselStatus.KANSELLERT
                },
                feilmelding = domene.feilmelding,
            )
        }
    }

    enum class EksterntVarselStatus {
        NY,
        SENDT,
        FEILET,
        KANSELLERT
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

    @JsonTypeName("KalenderavtaleData")
    data class KalenderavtaleData(
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
        val startTidspunkt: LocalDateTime,
        val sluttTidspunkt: LocalDateTime?,
        val lokasjon: Lokasjon?,
        val digitalt: Boolean,
        val tilstand: Notifikasjon.Kalenderavtale.Tilstand,
    )

    @JsonTypeName("Lokasjon")
    data class Lokasjon(
        val adresse: String,
        val postnummer: String,
        val poststed: String,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Mottaker {
        companion object {
            fun fraDomene(domene: HendelseModel.Mottaker): Mottaker {
                return when (domene) {
                    is HendelseModel.AltinnMottaker -> AltinnMottaker.fraDomene(domene)
                    is HendelseModel.NærmesteLederMottaker -> NærmesteLederMottaker.fraDomene(domene)
                    is HendelseModel._AltinnRolleMottaker -> basedOnEnv(
                        prod = { throw RuntimeException("AltinnRolleMottaker støttes ikke i prod") },
                        other = { AltinnMottaker("", "", "") },
                    )
                    is HendelseModel._AltinnReporteeMottaker -> basedOnEnv(
                        prod = { throw RuntimeException("AltinnReporteeMottaker støttes ikke i prod") },
                        other = { AltinnMottaker("", "", "") },
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
    ) : Mottaker() {
        companion object {
            fun fraDomene(domene: HendelseModel.NærmesteLederMottaker): NærmesteLederMottaker {
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
            fun fraDomene(domene: HendelseModel.AltinnMottaker): AltinnMottaker {
                return AltinnMottaker(
                    serviceCode = domene.serviceCode,
                    serviceEdition = domene.serviceEdition,
                    virksomhetsnummer = domene.virksomhetsnummer
                )
            }
        }
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface MineNotifikasjonerResultat

    private suspend fun mineNotifikasjoner(
        context: ProdusentAPI.Context,
        inputMerkelapp: String?,
        inputMerkelapper: List<String>?,
        grupperingsid: String?,
        first: Int,
        after: Cursor,
        env: DataFetchingEnvironment,
    ): MineNotifikasjonerResultat {

        val produsent = hentProdusent(context) { error -> return error }

        val merkelapper = when {
            inputMerkelapp == null && inputMerkelapper == null ->
                produsent.tillatteMerkelapper
            inputMerkelapp == null && inputMerkelapper != null ->
                inputMerkelapper
            inputMerkelapp != null && inputMerkelapper == null ->
                listOf(inputMerkelapp)
            else ->
                return Error.UgyldigMerkelapp("Kan ikke spesifiesere begge filtrene 'merkelapp' og 'merkelapper'")
        }

        for (merkelapp in merkelapper) {
            tilgangsstyrMerkelapp(produsent, merkelapp) { error -> return error }
        }

        return produsentRepository
            .finnNotifikasjoner(
                merkelapper = merkelapper,
                grupperingsid = grupperingsid,
                antall = first + 1,
                offset = after.offset
            ).map(Notifikasjon::fraDomene).let { notifikasjoner ->
                Connection.create(
                    data = notifikasjoner,
                    first = first,
                    after = after,
                    factory = ::NotifikasjonConnection,
                )
            }
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HentNotifikasjonResultat

    @JsonTypeName("HentetNotifikasjon")
    data class HentetNotifikasjon(
        val notifikasjon: Notifikasjon,
    ) : HentNotifikasjonResultat

    private suspend fun hentNotifikasjon(
        context: ProdusentAPI.Context,
        id: UUID,
    ): HentNotifikasjonResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val notifikasjon = produsentRepository.hentNotifikasjon(id)
            ?: return Error.NotifikasjonFinnesIkke("notifikasjon med id=$id finnes ikke")

        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

        return HentetNotifikasjon(Notifikasjon.fraDomene(notifikasjon))
    }
}

internal val QueryNotifikasjoner.Notifikasjon.metadata: QueryNotifikasjoner.Metadata
    get() = when (this) {
        is QueryNotifikasjoner.Notifikasjon.Beskjed -> this.metadata
        is QueryNotifikasjoner.Notifikasjon.Oppgave -> this.metadata
        is QueryNotifikasjoner.Notifikasjon.Kalenderavtale -> this.metadata
    }

internal val QueryNotifikasjoner.Notifikasjon.id: UUID
    get() = this.metadata.id

