package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.Companion.tilBrukerAPI
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.Scalars
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.TypedGraphQL
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.WithCoroutineScope
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.createGraphQL
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

object BrukerAPI {
    private val notifikasjonerHentetCount = Metrics.meterRegistry.counter("notifikasjoner_hentet")
    private val sakerHentetCount = Metrics.meterRegistry.counter("saker_hentet")
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
            val id: UUID,
            val brukerKlikk: BrukerKlikk,
            override val virksomhet: Virksomhet,
        ) : Notifikasjon(), WithVirksomhet

        @JsonTypeName("Oppgave")
        data class Oppgave(
            val merkelapp: String,
            val tekst: String,
            val lenke: String,
            val tilstand: Tilstand,
            val opprettetTidspunkt: OffsetDateTime,
            val utgaattTidspunkt: OffsetDateTime?,
            val frist: LocalDate?,
            val id: UUID,
            val brukerKlikk: BrukerKlikk,
            override val virksomhet: Virksomhet,
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
    }

    enum class SakSortering {
        OPPDATERT,
        OPPRETTET,
        FRIST,
    }

    @JsonTypeName("SakerResultat")
    data class SakerResultat(
        val saker: List<Sak>,
        val feilAltinn: Boolean,
        val totaltAntallSaker: Int,
    )

    @JsonTypeName("Sak")
    data class Sak(
        val id: UUID,
        val tittel: String,
        val lenke: String,
        val merkelapp: String,
        override val virksomhet: Virksomhet,
        val sisteStatus: SakStatus,
        val frister: List<LocalDate?>,
    ) : WithVirksomhet


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
            fun fraModel(model: HendelseModel.SakStatus) : SakStatusType = when(model) {
                HendelseModel.SakStatus.MOTTATT -> MOTTATT
                HendelseModel.SakStatus.UNDER_BEHANDLING -> UNDER_BEHANDLING
                HendelseModel.SakStatus.FERDIG -> FERDIG
            }
        }
    }

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
        tilgangerService: TilgangerService,
        virksomhetsinfoService: VirksomhetsinfoService,
    ) = TypedGraphQL<Context>(
        createGraphQL("/bruker.graphql") {
            scalar(Scalars.ISO8601DateTime)
            scalar(Scalars.ISO8601Date)

            resolveSubtypes<Notifikasjon>()
            resolveSubtypes<NotifikasjonKlikketPaaResultat>()

            wire("Query") {
                dataFetcher("whoami") {
                    it.getContext<Context>().fnr
                }

                queryNotifikasjoner(
                    brukerRepository = brukerRepository,
                    tilgangerService = tilgangerService,
                )

                querySaker(
                    brukerRepository = brukerRepository,
                    tilgangerService = tilgangerService,
                )

                wire("Oppgave") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Notifikasjon.Oppgave>(virksomhetsinfoService, env)
                    }
                }

                wire("Beskjed") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Notifikasjon.Beskjed>(virksomhetsinfoService, env)
                    }
                }

                wire("Sak") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Sak>(virksomhetsinfoService, env)
                    }
                }
            }

            wire("Mutation") {
                mutationBrukerKlikketPa(
                    brukerRepository = brukerRepository,
                    hendelseProdusent = hendelseProdusent,
                )
            }
        }
    )

    private fun TypeRuntimeWiring.Builder.queryNotifikasjoner(
        brukerRepository: BrukerRepository,
        tilgangerService: TilgangerService,
    ) {
        coDataFetcher("notifikasjoner") { env ->

            val context = env.getContext<Context>()
            val tilganger = tilgangerService.hentTilganger(context)

            val notifikasjoner = brukerRepository
                .hentNotifikasjoner(
                    context.fnr,
                    tilganger
                )
                .map { notifikasjon ->
                    when (notifikasjon) {
                        is BrukerModel.Beskjed ->
                            Notifikasjon.Beskjed(
                                merkelapp = notifikasjon.merkelapp,
                                tekst = notifikasjon.tekst,
                                lenke = notifikasjon.lenke,
                                opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                                id = notifikasjon.id,
                                virksomhet = Virksomhet(
                                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                                ),
                                brukerKlikk = BrukerKlikk(
                                    id = "${context.fnr}-${notifikasjon.id}",
                                    klikketPaa = notifikasjon.klikketPaa
                                )
                            )
                        is BrukerModel.Oppgave ->
                            Notifikasjon.Oppgave(
                                merkelapp = notifikasjon.merkelapp,
                                tekst = notifikasjon.tekst,
                                lenke = notifikasjon.lenke,
                                tilstand = notifikasjon.tilstand.tilBrukerAPI(),
                                opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                                utgaattTidspunkt = notifikasjon.utgaattTidspunkt,
                                frist = notifikasjon.frist,
                                id = notifikasjon.id,
                                virksomhet = Virksomhet(
                                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                                ),
                                brukerKlikk = BrukerKlikk(
                                    id = "${context.fnr}-${notifikasjon.id}",
                                    klikketPaa = notifikasjon.klikketPaa
                                )
                            )
                    }
                }
            notifikasjonerHentetCount.increment(notifikasjoner.size.toDouble())
            (if (tilganger.harFeil) altinnFeilCounter else altinnSuccessCounter).increment()

            return@coDataFetcher NotifikasjonerResultat(
                notifikasjoner,
                feilAltinn = tilganger.harFeil,
                feilDigiSyfo = false,
            )
        }
    }


    private fun TypeRuntimeWiring.Builder.querySaker(
        brukerRepository: BrukerRepository,
        tilgangerService: TilgangerService,
    ) {
        coDataFetcher("saker") { env ->
            val context = env.getContext<Context>()
            val virksomhetsnummer = env.getArgument<String>("virksomhetsnummer")
            val tekstsoek = env.getArgumentOrDefault<String>("tekstsoek", null)
            val sortering = env.getTypedArgument<SakSortering>("sortering")
            val offset = env.getArgumentOrDefault("offset", 0) ?: 0
            val limit = env.getArgumentOrDefault("limit", 3) ?: 3
            val tilganger = tilgangerService.hentTilganger(context)
            val sakerResultat = brukerRepository.hentSaker(
                fnr = context.fnr,
                virksomhetsnummer = virksomhetsnummer,
                tilganger = tilganger,
                tekstsoek = tekstsoek,
                sortering =  sortering,
                offset = offset,
                limit = limit,
            )
            val saker = sakerResultat.saker.map {
                Sak(
                    id = it.sakId,
                    tittel = it.tittel,
                    lenke = it.lenke,
                    merkelapp = it.merkelapp,
                    virksomhet = Virksomhet(
                        virksomhetsnummer = it.virksomhetsnummer,
                    ),
                    sisteStatus = it.statuser.map { sakStatus ->
                        val type = SakStatusType.fraModel(sakStatus.status)
                        SakStatus(
                            type = type,
                            tekst = sakStatus.overstyrtStatustekst ?: type.visningsTekst,
                            tidspunkt = sakStatus.tidspunkt
                        )
                    }.first(),
                    frister = it.frister,
                )
            }
            sakerHentetCount.increment(saker.size.toDouble())
            (if (tilganger.harFeil) altinnFeilCounter else altinnSuccessCounter).increment()
            SakerResultat(
                saker = saker,
                feilAltinn = tilganger.harFeil,
                totaltAntallSaker = sakerResultat.totaltAntallSaker
            )
        }
    }

    private fun TypeRuntimeWiring.Builder.mutationBrukerKlikketPa(
        brukerRepository: BrukerRepository,
        hendelseProdusent: HendelseProdusent,
    ) {
        coDataFetcher("notifikasjonKlikketPaa") { env ->
            val context = env.getContext<Context>()
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

            hendelseProdusent.send(hendelse)

            brukerRepository.oppdaterModellEtterHendelse(hendelse)

            BrukerKlikk(
                id = "${context.fnr}-${hendelse.notifikasjonId}",
                klikketPaa = true
            )
        }
    }

    private suspend fun <T : WithVirksomhet> fetchVirksomhet(
        virksomhetsinfoService: VirksomhetsinfoService,
        env: DataFetchingEnvironment
    ): Virksomhet {
        val source = env.getSource<T>()
        return if (env.selectionSet.contains("Virksomhet.navn")) {
            val underenhet = virksomhetsinfoService.hentUnderenhet(source.virksomhet.virksomhetsnummer)
            Virksomhet(
                virksomhetsnummer = underenhet.organisasjonsnummer,
                navn = underenhet.navn
            )
        } else {
            source.virksomhet
        }
    }
}