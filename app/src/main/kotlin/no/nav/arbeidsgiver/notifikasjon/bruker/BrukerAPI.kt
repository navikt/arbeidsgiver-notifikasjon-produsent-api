package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Kalenderavtale.Tilstand.Companion.tilBrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.Companion.tilBrukerAPI
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.tid.atOsloAsOffsetDateTime
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.util.*

object BrukerAPI {
    private val notifikasjonerHentetCount = Metrics.meterRegistry.counter("notifikasjoner_hentet")
    private val sakerHentetCount = Metrics.meterRegistry.counter("saker_hentet")
    private val sakstyperCounter = Metrics.meterRegistry.counter("saker_typer")
    private val altinnFeilCounter = Metrics.meterRegistry.counter("graphql.bruker.altinn.error")
    private val altinnSuccessCounter = Metrics.meterRegistry.counter("graphql.bruker.altinn.success")

    data class Context(
        val fnr: String,
        val token: String,
        override val coroutineScope: CoroutineScope
    ) : WithCoroutineScope

    interface WithVirksomhet {
        val virksomhet: Virksomhet
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Notifikasjon {

        @JsonTypeName("Beskjed")
        data class Beskjed(
            val merkelapp: String,
            val tekst: String,
            val lenke: String,
            val opprettetTidspunkt: OffsetDateTime,
            val sorteringTidspunkt: OffsetDateTime,
            val id: UUID,
            val brukerKlikk: BrukerKlikk,
            override val virksomhet: Virksomhet,
            val sak: SakMetadata?,
        ) : Notifikasjon(), WithVirksomhet

        @JsonTypeName("Oppgave")
        data class Oppgave(
            val merkelapp: String,
            val tekst: String,
            val lenke: String,
            val tilstand: Tilstand,
            val opprettetTidspunkt: OffsetDateTime,
            val sorteringTidspunkt: OffsetDateTime,
            val utgaattTidspunkt: OffsetDateTime?,
            val utfoertTidspunkt: OffsetDateTime?,
            val paaminnelseTidspunkt: OffsetDateTime?,
            val frist: LocalDate?,
            val id: UUID,
            val brukerKlikk: BrukerKlikk,
            override val virksomhet: Virksomhet,
            val sak: SakMetadata?,
        ) : Notifikasjon(), WithVirksomhet {
            enum class Tilstand {
                NY,
                UTFOERT,
                UTGAATT;

                companion object {
                    fun BrukerModel.Oppgave.Tilstand.tilBrukerAPI(): Tilstand = when (this) {
                        BrukerModel.Oppgave.Tilstand.NY -> NY
                        BrukerModel.Oppgave.Tilstand.UTFOERT -> UTFOERT
                        BrukerModel.Oppgave.Tilstand.UTGAATT -> UTGAATT
                    }
                }
            }
        }

        @JsonTypeName("Kalenderavtale")
        data class Kalenderavtale(
            val merkelapp: String,
            val tekst: String,
            val lenke: String,
            val avtaletilstand: Tilstand,
            val opprettetTidspunkt: OffsetDateTime,
            val sorteringTidspunkt: OffsetDateTime,
            val paaminnelseTidspunkt: OffsetDateTime?,
            val startTidspunkt: OffsetDateTime,
            val sluttTidspunkt: OffsetDateTime?,
            val lokasjon: Lokasjon?,
            val erDigitalt: Boolean?,
            val id: UUID,
            val brukerKlikk: BrukerKlikk,
            override val virksomhet: Virksomhet,
            val sak: SakMetadata?,
        ) : Notifikasjon(), WithVirksomhet {
            enum class Tilstand {
                VENTER_SVAR_FRA_ARBEIDSGIVER,
                ARBEIDSGIVER_VIL_AVLYSE,
                ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
                ARBEIDSGIVER_HAR_GODTATT,
                AVLYST,
                AVHOLDT;

                companion object {
                    fun BrukerModel.Kalenderavtale.Tilstand.tilBrukerAPI(): Tilstand = when (this) {
                        BrukerModel.Kalenderavtale.Tilstand.VENTER_SVAR_FRA_ARBEIDSGIVER -> VENTER_SVAR_FRA_ARBEIDSGIVER
                        BrukerModel.Kalenderavtale.Tilstand.ARBEIDSGIVER_VIL_AVLYSE -> ARBEIDSGIVER_VIL_AVLYSE
                        BrukerModel.Kalenderavtale.Tilstand.ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED -> ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED
                        BrukerModel.Kalenderavtale.Tilstand.ARBEIDSGIVER_HAR_GODTATT -> ARBEIDSGIVER_HAR_GODTATT
                        BrukerModel.Kalenderavtale.Tilstand.AVLYST -> AVLYST
                        BrukerModel.Kalenderavtale.Tilstand.AVHOLDT -> AVHOLDT
                    }
                }
            }
        }
    }

    @JsonTypeName("Lokasjon")
    data class Lokasjon(
        val adresse: String,
        val postnummer: String,
        val poststed: String,
    )

    @JsonTypeName("SakMetadata")
    data class SakMetadata(
        val tittel: String,
        val tilleggsinformasjon: String?
    )

    enum class SakSortering {
        NYESTE,
        ELDSTE
    }

    @JsonTypeName("SakerResultat")
    data class SakerResultat(
        val saker: List<Sak>,
        val sakstyper: List<Sakstype>,
        val feilAltinn: Boolean,
        val totaltAntallSaker: Int,
        @Deprecated("erstattes av oppgaveFilterInfo")
        val oppgaveTilstandInfo: List<OppgaveTilstandInfo>,
        val oppgaveFilterInfo: List<OppgaveFilterInfo>
    )

    @JsonTypeName("SakResultat")
    data class SakResultat(
        val sak: Sak?,
        val feilAltinn: Boolean,
    )

    @JsonTypeName("OppgaveTilstandInfo")
    @Deprecated("erstattes av OppgaveFilterInfo")
    data class OppgaveTilstandInfo(
        val tilstand: Oppgave.Tilstand,
        val antall: Int,
    )

    @JsonTypeName("OppgaveFilterInfo")
    data class OppgaveFilterInfo(
        val filterType: OppgaveFilterType,
        val antall: Int,
    ) {
        enum class OppgaveFilterType {
            TILSTAND_NY,
            TILSTAND_UTFOERT,
            TILSTAND_UTGAATT,
            TILSTAND_NY_MED_PAAMINNELSE_UTLOEST
        }
    }

    @JsonTypeName("OppgaveMetadata")
    data class OppgaveMetadata(
        val tilstand: Oppgave.Tilstand,
        val frist: LocalDate?,
        val paaminnelseTidspunkt: OffsetDateTime?,
    )

    @JsonTypeName("Sak")
    data class Sak(
        val id: UUID,
        val tittel: String,
        val lenke: String?,
        val merkelapp: String,
        override val virksomhet: Virksomhet,
        val sisteStatus: SakStatus,
        val nesteSteg: String?,
        val tilleggsinformasjon: String?,
        val frister: List<LocalDate?>,
        val oppgaver: List<OppgaveMetadata>,
        val tidslinje: List<TidslinjeElement>,
    ) : WithVirksomhet

    @JsonTypeName("Sakstype")
    data class Sakstype(
        val navn: String,
        val antall: Int,
    )

    @JsonTypeName("SakstypeOverordnet")
    data class SakstypeOverordnet(
        val navn: String,
    )

    @JsonTypeName("SakStatus")
    data class SakStatus(
        val type: SakStatusType,
        val tekst: String,
        val tidspunkt: OffsetDateTime,
    )

    enum class SakStatusType(val visningsTekst: String) {
        MOTTATT("Mottatt"),
        UNDER_BEHANDLING("Under behandling"),
        FERDIG("Ferdig");

        companion object {
            fun fraModel(model: HendelseModel.SakStatus): SakStatusType = when (model) {
                HendelseModel.SakStatus.MOTTATT -> MOTTATT
                HendelseModel.SakStatus.UNDER_BEHANDLING -> UNDER_BEHANDLING
                HendelseModel.SakStatus.FERDIG -> FERDIG
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class TidslinjeElement

    @JsonTypeName("OppgaveTidslinjeElement")
    data class OppgaveTidslinjeElement(
        val id: UUID,
        val tekst: String,
        val opprettetTidspunkt: OffsetDateTime,
        val tilstand: Oppgave.Tilstand,
        val paaminnelseTidspunkt: OffsetDateTime?,
        val utgaattTidspunkt: OffsetDateTime?,
        val utfoertTidspunkt: OffsetDateTime?,
        val frist: LocalDate?,
        val lenke: String
    ) : TidslinjeElement()

    @JsonTypeName("BeskjedTidslinjeElement")
    data class BeskjedTidslinjeElement(
        val id: UUID,
        val tekst: String,
        val opprettetTidspunkt: OffsetDateTime,
        val lenke: String
    ) : TidslinjeElement()

    @JsonTypeName("KalenderavtaleTidslinjeElement")
    data class KalenderavtaleTidslinjeElement(
        val id: UUID,
        val tekst: String,
        val avtaletilstand: Notifikasjon.Kalenderavtale.Tilstand,
        val startTidspunkt: OffsetDateTime,
        val sluttTidspunkt: OffsetDateTime?,
        val lokasjon: Lokasjon?,
        val digitalt: Boolean?,
        val lenke: String
    ) : TidslinjeElement()


    @JsonTypeName("NotifikasjonsPanelApnetResultat")
    data class NotifikasjonsPanelApnetResultat(
        val tidspunkt: OffsetDateTime?
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class NotifikasjonKlikketPaaResultat

    @JsonTypeName("BrukerKlikk")
    data class BrukerKlikk(
        val id: String,
        val klikketPaa: Boolean
    ) : NotifikasjonKlikketPaaResultat()

    @JsonTypeName("NotifikasjonerResultat")
    data class NotifikasjonerResultat(
        val notifikasjoner: List<Notifikasjon>,
        val feilAltinn: Boolean,
        val feilDigiSyfo: Boolean
    )

    @JsonTypeName("KalenderavtalerResultat")
    data class KalenderavtalerResultat(
        val avtaler: List<Notifikasjon.Kalenderavtale>,
        val feilAltinn: Boolean,
        val feilDigiSyfo: Boolean
    )

    @JsonTypeName("UgyldigId")
    data class UgyldigId(
        val feilmelding: String
    ) : NotifikasjonKlikketPaaResultat()

    data class Virksomhet(
        val virksomhetsnummer: String,
        val navn: String? = null
    )

    fun createBrukerGraphQL(
        brukerRepository: BrukerRepository,
        hendelseProdusent: HendelseProdusent,
        altinnTilgangerService: AltinnTilgangerService,
        virksomhetsinfoService: VirksomhetsinfoService,
    ) = TypedGraphQL<Context>(
        createGraphQL("/bruker.graphql") {
            scalar(Scalars.ISO8601DateTime)
            scalar(Scalars.ISO8601Date)

            resolveSubtypes<Notifikasjon>()
            resolveSubtypes<NotifikasjonKlikketPaaResultat>()
            resolveSubtypes<TidslinjeElement>()

            wire("Query") {
                dataFetcher("whoami") {
                    it.notifikasjonContext<Context>().fnr
                }

                queryNotifikasjoner(brukerRepository, altinnTilgangerService)

                querySaker(brukerRepository, altinnTilgangerService)

                querySak(brukerRepository, altinnTilgangerService)

                querySakstyper(brukerRepository, altinnTilgangerService)

                queryKommendeKalenderavtaler(brukerRepository, altinnTilgangerService)

                queryNotifikasjonsPanelÅpnet(brukerRepository)

                wire("Oppgave") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Oppgave>(virksomhetsinfoService, env)
                    }
                }

                wire("Beskjed") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Notifikasjon.Beskjed>(virksomhetsinfoService, env)
                    }
                }

                wire("Kalenderavtale") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Notifikasjon.Kalenderavtale>(virksomhetsinfoService, env)
                    }
                }

                wire("Sak") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Sak>(virksomhetsinfoService, env)
                    }
                }
            }

            wire("Mutation") {
                mutationBrukerKlikketPa(brukerRepository, hendelseProdusent)
                mutationNotifikasjonsPanelÅpnet(brukerRepository)
            }
        }
    )

    private fun TypeRuntimeWiring.Builder.mutationNotifikasjonsPanelÅpnet(brukerRepository: BrukerRepository) {
        coDataFetcher("notifikasjonPanelApnet") { env ->
            val context = env.notifikasjonContext<Context>()
            val tidspunkt = env.getTypedArgument<OffsetDateTime>("tidspunkt")
            brukerRepository.settNotifikasjonerSistLest(tidspunkt, context.fnr)
            NotifikasjonsPanelApnetResultat(
                tidspunkt = tidspunkt
            )
        }
    }

    private fun TypeRuntimeWiring.Builder.queryNotifikasjonsPanelÅpnet(brukerRepository: BrukerRepository) {
        coDataFetcher("notifikasjonPanelApnet") { env ->
            val context = env.notifikasjonContext<Context>()
            val tidspunkt = brukerRepository.hentNotifikasjonerSistLest(context.fnr)
            NotifikasjonsPanelApnetResultat(
                tidspunkt = tidspunkt
            )
        }
    }

    private fun TypeRuntimeWiring.Builder.querySakstyper(
        brukerRepository: BrukerRepository,
        altinnTilgangerService: AltinnTilgangerService
    ) {
        coDataFetcher("sakstyper") { env ->
            val context = env.notifikasjonContext<Context>()
            val tilganger = altinnTilgangerService.hentTilganger(context.fnr, context.token)
            (if (tilganger.harFeil) altinnFeilCounter else altinnSuccessCounter).increment()

            val sakstyper = brukerRepository.hentSakstyper(context.fnr, tilganger)

            /* TODO: rapportere om feil med altinn? Det vil jo påvirke * filteret ... */
            sakstyperCounter.increment(sakstyper.size.toDouble())
            return@coDataFetcher sakstyper.map {
                SakstypeOverordnet(it)
            }
        }
    }

    private fun TypeRuntimeWiring.Builder.queryNotifikasjoner(
        brukerRepository: BrukerRepository,
        altinnTilgangerService: AltinnTilgangerService,
    ) {
        coDataFetcher("notifikasjoner") { env ->

            val context = env.notifikasjonContext<Context>()
            val tilganger = altinnTilgangerService.hentTilganger(
                context.fnr,
                context.token
            )

            val notifikasjonerDb = brukerRepository.hentNotifikasjoner(context.fnr, tilganger)
            val sakstitler = brukerRepository.hentSakerForNotifikasjoner(
                notifikasjonerDb.mapNotNull { it.gruppering },
            )
            val notifikasjoner = notifikasjonerDb
                .map { notifikasjon ->
                    when (notifikasjon) {
                        is BrukerModel.Beskjed ->
                            Notifikasjon.Beskjed(
                                merkelapp = notifikasjon.merkelapp,
                                tekst = notifikasjon.tekst,
                                lenke = notifikasjon.lenke,
                                opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                                sorteringTidspunkt = notifikasjon.sorteringTidspunkt,
                                id = notifikasjon.id,
                                virksomhet = Virksomhet(
                                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                                ),
                                brukerKlikk = BrukerKlikk(
                                    id = "${context.fnr}-${notifikasjon.id}",
                                    klikketPaa = notifikasjon.klikketPaa
                                ),
                                sak = sakstitler[notifikasjon.grupperingsid]?.let {
                                    SakMetadata(
                                        tittel = it.tittel,
                                        tilleggsinformasjon = it.tilleggsinformasjon
                                    )
                                },
                            )

                        is BrukerModel.Oppgave ->
                            Oppgave(
                                merkelapp = notifikasjon.merkelapp,
                                tekst = notifikasjon.tekst,
                                lenke = notifikasjon.lenke,
                                tilstand = notifikasjon.tilstand.tilBrukerAPI(),
                                opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                                sorteringTidspunkt = notifikasjon.sorteringTidspunkt,
                                utgaattTidspunkt = notifikasjon.utgaattTidspunkt,
                                paaminnelseTidspunkt = notifikasjon.paaminnelseTidspunkt,
                                utfoertTidspunkt = notifikasjon.utfoertTidspunkt,
                                frist = notifikasjon.frist,
                                id = notifikasjon.id,
                                virksomhet = Virksomhet(
                                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                                ),
                                brukerKlikk = BrukerKlikk(
                                    id = "${context.fnr}-${notifikasjon.id}",
                                    klikketPaa = notifikasjon.klikketPaa
                                ),
                                sak = sakstitler[notifikasjon.grupperingsid]?.let {
                                    SakMetadata(
                                        tittel = it.tittel,
                                        tilleggsinformasjon = it.tilleggsinformasjon
                                    )
                                },
                            )

                        is BrukerModel.Kalenderavtale ->
                            Notifikasjon.Kalenderavtale(
                                merkelapp = notifikasjon.merkelapp,
                                tekst = notifikasjon.tekst,
                                lenke = notifikasjon.lenke,
                                avtaletilstand = notifikasjon.tilstand.tilBrukerAPI(),
                                opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                                sorteringTidspunkt = notifikasjon.sorteringTidspunkt,
                                paaminnelseTidspunkt = notifikasjon.paaminnelseTidspunkt,
                                startTidspunkt = notifikasjon.startTidspunkt.atOsloAsOffsetDateTime(),
                                sluttTidspunkt = notifikasjon.sluttTidspunkt?.atOsloAsOffsetDateTime(),
                                lokasjon = notifikasjon.lokasjon?.let {
                                    Lokasjon(
                                        adresse = it.adresse,
                                        postnummer = it.postnummer,
                                        poststed = it.poststed,
                                    )
                                },
                                erDigitalt = notifikasjon.erDigitalt,
                                id = notifikasjon.id,
                                virksomhet = Virksomhet(
                                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                                ),
                                brukerKlikk = BrukerKlikk(
                                    id = "${context.fnr}-${notifikasjon.id}",
                                    klikketPaa = notifikasjon.klikketPaa
                                ),
                                sak = sakstitler[notifikasjon.grupperingsid]?.let {
                                    SakMetadata(
                                        tittel = it.tittel,
                                        tilleggsinformasjon = it.tilleggsinformasjon
                                    )
                                },
                            )
                    }
                }
            notifikasjonerHentetCount.increment(notifikasjoner.size.toDouble())
            (if (tilganger.harFeil) altinnFeilCounter else altinnSuccessCounter).increment()

            return@coDataFetcher NotifikasjonerResultat(
                notifikasjoner = notifikasjoner,
                feilAltinn = tilganger.harFeil,
                feilDigiSyfo = false,
            )
        }
    }

    private fun TypeRuntimeWiring.Builder.queryKommendeKalenderavtaler(
        brukerRepository: BrukerRepository,
        altinnTilgangerService: AltinnTilgangerService,
    ) {
        coDataFetcher("kommendeKalenderavtaler") { env ->

            val context = env.notifikasjonContext<Context>()
            val virksomhetsnumre = env.getTypedArgument<List<String>>("virksomhetsnumre")

            val tilganger = altinnTilgangerService.hentTilganger(context.fnr, context.token)

            val kalenderAvtalerDb: List<BrukerModel.Kalenderavtale> =
                brukerRepository.hentKommendeKalenderavaler(context.fnr, virksomhetsnumre, tilganger)
            val sakstitler = brukerRepository.hentSakerForNotifikasjoner(
                kalenderAvtalerDb.mapNotNull { it.gruppering },
            )
            val kalenderavtaler = kalenderAvtalerDb
                .map { notifikasjon ->
                    Notifikasjon.Kalenderavtale(
                        merkelapp = notifikasjon.merkelapp,
                        tekst = notifikasjon.tekst,
                        lenke = notifikasjon.lenke,
                        avtaletilstand = notifikasjon.tilstand.tilBrukerAPI(),
                        opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                        sorteringTidspunkt = notifikasjon.sorteringTidspunkt,
                        paaminnelseTidspunkt = notifikasjon.paaminnelseTidspunkt,
                        startTidspunkt = notifikasjon.startTidspunkt.atOsloAsOffsetDateTime(),
                        sluttTidspunkt = notifikasjon.sluttTidspunkt?.atOsloAsOffsetDateTime(),
                        lokasjon = notifikasjon.lokasjon?.let {
                            Lokasjon(
                                adresse = it.adresse,
                                postnummer = it.postnummer,
                                poststed = it.poststed,
                            )
                        },
                        erDigitalt = notifikasjon.erDigitalt,
                        id = notifikasjon.id,
                        virksomhet = Virksomhet(
                            virksomhetsnummer = notifikasjon.virksomhetsnummer,
                        ),
                        brukerKlikk = BrukerKlikk(
                            id = "${context.fnr}-${notifikasjon.id}",
                            klikketPaa = notifikasjon.klikketPaa
                        ),
                        sak = sakstitler[notifikasjon.grupperingsid]?.let {
                            SakMetadata(
                                tittel = it.tittel,
                                tilleggsinformasjon = it.tilleggsinformasjon
                            )
                        },
                    )
                }
            (if (tilganger.harFeil) altinnFeilCounter else altinnSuccessCounter).increment()

            return@coDataFetcher KalenderavtalerResultat(
                avtaler = kalenderavtaler,
                feilAltinn = tilganger.harFeil,
                feilDigiSyfo = false,
            )
        }
    }


    private fun TypeRuntimeWiring.Builder.querySaker(
        brukerRepository: BrukerRepository,
        altinnTilgangerService: AltinnTilgangerService,
    ) {
        coDataFetcher("saker") { env ->
            val context = env.notifikasjonContext<Context>()

            // TODO: fjern fallback når ingen klienter kaller med den lengre
            val virksomhetsnumre = env.getTypedArgumentOrDefault<List<String>>("virksomhetsnumre") {
                listOf(env.getTypedArgument("virksomhetsnummer"))
            }

            val tilganger = altinnTilgangerService.hentTilganger(context.fnr, context.token)
            val sakerResultat = brukerRepository.hentSaker(
                fnr = context.fnr,
                virksomhetsnummer = virksomhetsnumre,
                altinnTilganger = tilganger,
                sakstyper = env.getArgument("sakstyper"),
                tekstsoek = env.getArgumentOrDefault<String>("tekstsoek", null),
                sortering = env.getTypedArgument("sortering"),
                offset = env.getArgumentOrDefault("offset", 0) ?: 0,
                limit = env.getArgumentOrDefault("limit", 3) ?: 3,
                oppgaveTilstand = env.getTypedArgumentOrNull("oppgaveTilstand"),
                oppgaveFilter = env.getTypedArgumentOrNull("oppgaveFilter"),
            )
            val berikelser = brukerRepository.berikSaker(sakerResultat.saker)
            val saker = sakerResultat.saker.map {
                val berikelse = berikelser[it.sakId]
                val oppgaver = berikelse?.tidslinje.orEmpty()
                    .filterIsInstance<BrukerModel.TidslinjeElement.Oppgave>()
                    .sortedWith { left, right ->
                        when {
                            left.frist == null -> 1
                            right.frist == null -> -1
                            else -> left.frist.compareTo(right.frist)
                        }
                    }
                Sak(
                    id = it.sakId,
                    tittel = it.tittel,
                    lenke = it.lenke,
                    merkelapp = it.merkelapp,
                    virksomhet = Virksomhet(
                        virksomhetsnummer = it.virksomhetsnummer,
                    ),
                    sisteStatus = when (val sisteStatus = berikelse?.sisteStatus) {
                        null -> SakStatus(
                            type = SakStatusType.MOTTATT,
                            tekst = SakStatusType.MOTTATT.visningsTekst,
                            tidspunkt = it.opprettetTidspunkt.atOffset(UTC),
                        )

                        else -> {
                            val type = SakStatusType.fraModel(sisteStatus.status)
                            SakStatus(
                                type = type,
                                tekst = sisteStatus.overstyrtStatustekst ?: type.visningsTekst,
                                tidspunkt = sisteStatus.tidspunkt
                            )
                        }
                    },
                    frister = oppgaver
                        .filter { oppgave -> oppgave.tilstand == BrukerModel.Oppgave.Tilstand.NY }
                        .map { oppgave -> oppgave.frist },
                    nesteSteg = it.nesteSteg,
                    tilleggsinformasjon = it.tilleggsinformasjon,
                    oppgaver = oppgaver.map { o ->
                        OppgaveMetadata(
                            tilstand = o.tilstand.tilBrukerAPI(),
                            frist = o.frist,
                            paaminnelseTidspunkt = o.paaminnelseTidspunkt?.atOffset(UTC),
                        )
                    },
                    tidslinje = berikelse?.tidslinje.orEmpty().map { element ->
                        when (element) {
                            is BrukerModel.TidslinjeElement.Oppgave -> OppgaveTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                opprettetTidspunkt = element.opprettetTidspunkt.atOffset(UTC),
                                tilstand = element.tilstand.tilBrukerAPI(),
                                paaminnelseTidspunkt = element.paaminnelseTidspunkt?.atOffset(UTC),
                                utgaattTidspunkt = element.utgaattTidspunkt?.atOffset(UTC),
                                utfoertTidspunkt = element.utfoertTidspunkt?.atOffset(UTC),
                                frist = element.frist,
                                lenke = element.lenke,
                            )

                            is BrukerModel.TidslinjeElement.Beskjed -> BeskjedTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                opprettetTidspunkt = element.opprettetTidspunkt.atOffset(UTC),
                                lenke = element.lenke
                            )

                            is BrukerModel.TidslinjeElement.Kalenderavtale -> KalenderavtaleTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,

                                avtaletilstand = element.avtaletilstand.tilBrukerAPI(),
                                startTidspunkt = element.startTidspunkt.atOsloAsOffsetDateTime(),
                                sluttTidspunkt = element.sluttTidspunkt?.atOsloAsOffsetDateTime(),
                                lokasjon = element.lokasjon?.let { loc ->
                                    Lokasjon(
                                        adresse = loc.adresse,
                                        postnummer = loc.postnummer,
                                        poststed = loc.poststed,
                                    )
                                },
                                digitalt = element.digitalt,
                                lenke = element.lenke
                            )
                        }
                    }
                )
            }
            sakerHentetCount.increment(saker.size.toDouble())
            (if (tilganger.harFeil) altinnFeilCounter else altinnSuccessCounter).increment()
            SakerResultat(
                saker = saker,
                sakstyper = sakerResultat.sakstyper.map { sakstype -> Sakstype(sakstype.navn, sakstype.antall) },
                feilAltinn = tilganger.harFeil,
                totaltAntallSaker = sakerResultat.totaltAntallSaker,
                oppgaveTilstandInfo = sakerResultat.oppgaveTilstanderMedAntall.map { tilstand ->
                    OppgaveTilstandInfo(
                        tilstand.navn.tilBrukerAPI(),
                        tilstand.antall
                    )
                },
                oppgaveFilterInfo = sakerResultat.oppgaveFilterMedAntall.mapNotNull { filter ->
                    val filterType = filter.filterType.tilBrukerApi()
                    if (filterType !== null)
                        OppgaveFilterInfo(
                            filterType = filterType,
                            antall = filter.antall
                        )
                    else null
                }
            )
        }
    }

    private fun TypeRuntimeWiring.Builder.querySak(
        brukerRepository: BrukerRepository,
        altinnTilgangerService: AltinnTilgangerService,
    ) {
        coDataFetcher("sakById") { env ->
            val context = env.notifikasjonContext<Context>()

            val tilganger = altinnTilgangerService.hentTilganger(context.fnr, context.token)

            val sak = brukerRepository.hentSakById(
                fnr = context.fnr,
                altinnTilganger = tilganger,
                id = env.getTypedArgument("id"),
            ) ?: return@coDataFetcher SakResultat(
                sak = null,
                feilAltinn = tilganger.harFeil,
            )

            val berikelse = brukerRepository.berikSaker(listOf(sak))[sak.sakId]
            val oppgaver = berikelse?.tidslinje.orEmpty()
                .filterIsInstance<BrukerModel.TidslinjeElement.Oppgave>()
                .sortedWith { left, right ->
                    when {
                        left.frist == null -> 1
                        right.frist == null -> -1
                        else -> left.frist.compareTo(right.frist)
                    }
                }

            SakResultat(
                sak = Sak(
                    id = sak.sakId,
                    tittel = sak.tittel,
                    lenke = sak.lenke,
                    merkelapp = sak.merkelapp,
                    virksomhet = Virksomhet(
                        virksomhetsnummer = sak.virksomhetsnummer,
                    ),
                    sisteStatus = when (val sisteStatus = berikelse?.sisteStatus) {
                        null -> SakStatus(
                            type = SakStatusType.MOTTATT,
                            tekst = SakStatusType.MOTTATT.visningsTekst,
                            tidspunkt = sak.opprettetTidspunkt.atOffset(UTC),
                        )

                        else -> {
                            val type = SakStatusType.fraModel(sisteStatus.status)
                            SakStatus(
                                type = type,
                                tekst = sisteStatus.overstyrtStatustekst ?: type.visningsTekst,
                                tidspunkt = sisteStatus.tidspunkt
                            )
                        }
                    },
                    nesteSteg = sak.nesteSteg,
                    tilleggsinformasjon = sak.tilleggsinformasjon,
                    frister = oppgaver
                        .filter { it.tilstand == BrukerModel.Oppgave.Tilstand.NY }
                        .map { it.frist },
                    oppgaver = oppgaver.map { o ->
                        OppgaveMetadata(
                            tilstand = o.tilstand.tilBrukerAPI(),
                            frist = o.frist,
                            paaminnelseTidspunkt = o.paaminnelseTidspunkt?.atOffset(UTC),
                        )
                    },
                    tidslinje = berikelse?.tidslinje.orEmpty().map { element ->
                        when (element) {
                            is BrukerModel.TidslinjeElement.Oppgave -> OppgaveTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                opprettetTidspunkt = element.opprettetTidspunkt.atOffset(UTC),
                                tilstand = element.tilstand.tilBrukerAPI(),
                                paaminnelseTidspunkt = element.paaminnelseTidspunkt?.atOffset(UTC),
                                utgaattTidspunkt = element.utgaattTidspunkt?.atOffset(UTC),
                                utfoertTidspunkt = element.utfoertTidspunkt?.atOffset(UTC),
                                frist = element.frist,
                                lenke = element.lenke,
                            )

                            is BrukerModel.TidslinjeElement.Beskjed -> BeskjedTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                opprettetTidspunkt = element.opprettetTidspunkt.atOffset(UTC),
                                lenke = element.lenke,
                            )

                            is BrukerModel.TidslinjeElement.Kalenderavtale -> KalenderavtaleTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                avtaletilstand = element.avtaletilstand.tilBrukerAPI(),
                                startTidspunkt = element.startTidspunkt.atOsloAsOffsetDateTime(),
                                sluttTidspunkt = element.sluttTidspunkt?.atOsloAsOffsetDateTime(),
                                lokasjon = element.lokasjon?.let {
                                    Lokasjon(
                                        adresse = it.adresse,
                                        postnummer = it.postnummer,
                                        poststed = it.poststed,
                                    )
                                },
                                digitalt = element.digitalt,
                                lenke = element.lenke,
                            )
                        }
                    }
                ),
                feilAltinn = tilganger.harFeil,
            )
        }

        coDataFetcher("sakByGrupperingsid") { env ->
            val context = env.notifikasjonContext<Context>()

            val tilganger = altinnTilgangerService.hentTilganger(context.fnr, context.token)

            val sak = brukerRepository.hentSakByGrupperingsid(
                fnr = context.fnr,
                altinnTilganger = tilganger,
                grupperingsid = env.getTypedArgument("grupperingsid"),
                merkelapp = env.getTypedArgument("merkelapp"),
            ) ?: return@coDataFetcher SakResultat(
                sak = null,
                feilAltinn = tilganger.harFeil,
            )

            val berikelse = brukerRepository.berikSaker(listOf(sak))[sak.sakId]
            val oppgaver = berikelse?.tidslinje.orEmpty()
                .filterIsInstance<BrukerModel.TidslinjeElement.Oppgave>()
                .sortedWith { left, right ->
                    when {
                        left.frist == null -> 1
                        right.frist == null -> -1
                        else -> left.frist.compareTo(right.frist)
                    }
                }

            SakResultat(
                sak = Sak(
                    id = sak.sakId,
                    tittel = sak.tittel,
                    lenke = sak.lenke,
                    merkelapp = sak.merkelapp,
                    virksomhet = Virksomhet(
                        virksomhetsnummer = sak.virksomhetsnummer,
                    ),
                    sisteStatus = when (val sisteStatus = berikelse?.sisteStatus) {
                        null -> SakStatus(
                            type = SakStatusType.MOTTATT,
                            tekst = SakStatusType.MOTTATT.visningsTekst,
                            tidspunkt = sak.opprettetTidspunkt.atOffset(UTC),
                        )

                        else -> {
                            val type = SakStatusType.fraModel(sisteStatus.status)
                            SakStatus(
                                type = type,
                                tekst = sisteStatus.overstyrtStatustekst ?: type.visningsTekst,
                                tidspunkt = sisteStatus.tidspunkt
                            )
                        }
                    },
                    nesteSteg = sak.nesteSteg,
                    tilleggsinformasjon = sak.tilleggsinformasjon,
                    frister = oppgaver
                        .filter { it.tilstand == BrukerModel.Oppgave.Tilstand.NY }
                        .map { it.frist },
                    oppgaver = oppgaver.map { o ->
                        OppgaveMetadata(
                            tilstand = o.tilstand.tilBrukerAPI(),
                            frist = o.frist,
                            paaminnelseTidspunkt = o.paaminnelseTidspunkt?.atOffset(UTC),
                        )
                    },
                    tidslinje = berikelse?.tidslinje.orEmpty().map { element ->
                        when (element) {
                            is BrukerModel.TidslinjeElement.Oppgave -> OppgaveTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                opprettetTidspunkt = element.opprettetTidspunkt.atOffset(UTC),
                                tilstand = element.tilstand.tilBrukerAPI(),
                                paaminnelseTidspunkt = element.paaminnelseTidspunkt?.atOffset(UTC),
                                utgaattTidspunkt = element.utgaattTidspunkt?.atOffset(UTC),
                                utfoertTidspunkt = element.utfoertTidspunkt?.atOffset(UTC),
                                frist = element.frist,
                                lenke = element.lenke,
                            )

                            is BrukerModel.TidslinjeElement.Beskjed -> BeskjedTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                opprettetTidspunkt = element.opprettetTidspunkt.atOffset(UTC),
                                lenke = element.lenke,
                            )

                            is BrukerModel.TidslinjeElement.Kalenderavtale -> KalenderavtaleTidslinjeElement(
                                id = element.id,
                                tekst = element.tekst,
                                avtaletilstand = element.avtaletilstand.tilBrukerAPI(),
                                startTidspunkt = element.startTidspunkt.atOsloAsOffsetDateTime(),
                                sluttTidspunkt = element.sluttTidspunkt?.atOsloAsOffsetDateTime(),
                                lokasjon = element.lokasjon?.let {
                                    Lokasjon(
                                        adresse = it.adresse,
                                        postnummer = it.postnummer,
                                        poststed = it.poststed,
                                    )
                                },
                                digitalt = element.digitalt,
                                lenke = element.lenke,
                            )
                        }
                    }
                ),
                feilAltinn = tilganger.harFeil,
            )
        }
    }

    private fun TypeRuntimeWiring.Builder.mutationBrukerKlikketPa(
        brukerRepository: BrukerRepository,
        hendelseProdusent: HendelseProdusent,
    ) {
        coDataFetcher("notifikasjonKlikketPaa") { env ->
            val context = env.notifikasjonContext<Context>()
            val notifikasjonsid = env.getTypedArgument<UUID>("id")

            val virksomhetsnummer = brukerRepository.virksomhetsnummerForNotifikasjon(notifikasjonsid)
                ?: return@coDataFetcher UgyldigId("")

            val hendelse = BrukerKlikket(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = notifikasjonsid,
                fnr = context.fnr,
                virksomhetsnummer = virksomhetsnummer,
                kildeAppNavn = NaisEnvironment.clientId,
            )

            val metadata = hendelseProdusent.sendOgHentMetadata(hendelse)

            brukerRepository.oppdaterModellEtterHendelse(hendelse, metadata)

            BrukerKlikk(
                id = "${context.fnr}-${hendelse.notifikasjonId}",
                klikketPaa = true
            )
        }
    }

    private suspend fun <T : WithVirksomhet> fetchVirksomhet(
        virksomhetsinfoService: VirksomhetsinfoService,
        env: DataFetchingEnvironment
    ): Virksomhet? {
        val source = env.getSource<T>()
        return if (source != null && env.selectionSet.contains("Virksomhet.navn")) {
            val underenhet = virksomhetsinfoService.hentUnderenhet(source.virksomhet.virksomhetsnummer)
            Virksomhet(
                virksomhetsnummer = underenhet.organisasjonsnummer,
                navn = underenhet.navn
            )
        } else {
            source?.virksomhet
        }
    }

    private val log = logger()

    // Parser kolonnenavn fra sql spørringen i hentSaker
    private fun String.tilBrukerApi(): OppgaveFilterInfo.OppgaveFilterType? {
        return when (this) {
            Oppgave.Tilstand.NY.name -> OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY
            Oppgave.Tilstand.UTGAATT.name -> OppgaveFilterInfo.OppgaveFilterType.TILSTAND_UTGAATT
            Oppgave.Tilstand.UTFOERT.name -> OppgaveFilterInfo.OppgaveFilterType.TILSTAND_UTFOERT
            OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST.name -> OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST
            else -> {
                log.warn("Ukjent oppgavefiltertype: $this")
                null
            }

        }
    }
}

