package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.Companion.tilBrukerAPI
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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import java.time.OffsetDateTime
import java.util.*

object BrukerAPI {
    private val notifikasjonerHentetCount = Metrics.meterRegistry.counter("notifikasjoner_hentet")
    private val sakerHentetCount = Metrics.meterRegistry.counter("saker_hentet")

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
            val id: UUID,
            val brukerKlikk: BrukerKlikk,
            override val virksomhet: Virksomhet,
        ) : Notifikasjon(), WithVirksomhet {
            enum class Tilstand {
                NY,
                UTFOERT;

                companion object {
                    fun BrukerModel.Oppgave.Tilstand.tilBrukerAPI(): Tilstand = when (this) {
                        BrukerModel.Oppgave.Tilstand.NY -> NY
                        BrukerModel.Oppgave.Tilstand.UTFOERT -> UTFOERT
                    }
                }
            }
        }
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
        val sisteStatus: SakStatus
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
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
        tilgangerService: TilgangerService,
        virksomhetsinfoService: VirksomhetsinfoService,
    ) = TypedGraphQL<Context>(
        createGraphQL("/bruker.graphql") {
            scalar(Scalars.ISO8601DateTime)

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
                    kafkaProducer = kafkaProducer,
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
            val tilganger =  tilgangerService.hentTilganger(context)

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
            val offset = env.getArgumentOrDefault("offset", 0) ?: 0
            val limit = env.getArgumentOrDefault("limit", 3) ?: 3
            val tilganger = tilgangerService.hentTilganger(context)
            val sakerResultat = brukerRepository.hentSaker(
                fnr = context.fnr,
                virksomhetsnummer = virksomhetsnummer,
                tilganger = tilganger,
                tekstsoek = tekstsoek,
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
                )
            }
            sakerHentetCount.increment(saker.size.toDouble())
            SakerResultat(
                saker = saker,
                feilAltinn = tilganger.harFeil,
                totaltAntallSaker = sakerResultat.totaltAntallSaker
            )
        }
    }

    private fun TypeRuntimeWiring.Builder.mutationBrukerKlikketPa(
        brukerRepository: BrukerRepository,
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
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

            kafkaProducer.sendHendelse(hendelse)

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