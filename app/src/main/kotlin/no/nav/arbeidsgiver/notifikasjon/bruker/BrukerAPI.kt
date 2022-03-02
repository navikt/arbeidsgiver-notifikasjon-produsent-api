package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleService
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.Companion.tilBrukerAPI
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.MottakerRegister
import java.time.OffsetDateTime
import java.util.*

object BrukerAPI {
    private val log = logger()

    private val naisClientId = System.getenv("NAIS_CLIENT_ID") ?: "local:fager:notifikasjon-bruker-api"

    private val notifikasjonerHentetCount = Health.meterRegistry.counter("notifikasjoner_hentet")

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
        val feilAltinn: Boolean
    )

    @JsonTypeName("Sak")
    data class Sak(
        val id: String,
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
            fun fraModel(model: no.nav.arbeidsgiver.notifikasjon.SakStatus) : SakStatusType = when(model) {
                no.nav.arbeidsgiver.notifikasjon.SakStatus.MOTTATT -> MOTTATT
                no.nav.arbeidsgiver.notifikasjon.SakStatus.UNDER_BEHANDLING -> UNDER_BEHANDLING
                no.nav.arbeidsgiver.notifikasjon.SakStatus.FERDIG -> FERDIG
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
        altinn: Altinn,
        altinnRolleService: AltinnRolleService,
        enhetsregisteret: Enhetsregisteret,
        brukerRepository: BrukerRepository,
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
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
                    altinn = altinn,
                    brukerRepository = brukerRepository,
                    altinnRolleService = altinnRolleService
                )

                querySaker(
                    altinn = altinn,
                    brukerRepository = brukerRepository,
                    altinnRolleService = altinnRolleService
                )

                wire("Oppgave") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Notifikasjon.Oppgave>(enhetsregisteret, env)
                    }
                }

                wire("Beskjed") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Notifikasjon.Beskjed>(enhetsregisteret, env)
                    }
                }

                wire("Sak") {
                    coDataFetcher("virksomhet") { env ->
                        fetchVirksomhet<Sak>(enhetsregisteret, env)
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

    fun TypeRuntimeWiring.Builder.queryNotifikasjoner(
        altinn: Altinn,
        brukerRepository: BrukerRepository,
        altinnRolleService: AltinnRolleService,
    ) {
        coDataFetcher("notifikasjoner") { env ->

            val context = env.getContext<Context>()
            coroutineScope {
                val tilganger = async {
                    try {
                        altinn.hentTilganger(
                            context.fnr,
                            context.token,
                            MottakerRegister.servicecodeDefinisjoner,
                            altinnRolleService.hentRoller(MottakerRegister.rolleDefinisjoner),
                        )
                    } catch (e: AltinnrettigheterProxyKlientFallbackException) {
                        if (e.erDriftsforstyrrelse())
                            log.info("Henting av Altinn-tilganger feilet", e)
                        else
                            log.error("Henting av Altinn-tilganger feilet", e)
                        null
                    } catch (e: Exception) {
                        log.error("Henting av Altinn-tilganger feilet", e)
                        null
                    }
                }

                val notifikasjoner = brukerRepository
                    .hentNotifikasjoner(
                        context.fnr,
                        tilganger.await().orEmpty()
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
                return@coroutineScope NotifikasjonerResultat(
                    notifikasjoner,
                    feilAltinn = tilganger.await() == null,
                    feilDigiSyfo = false,
                )
            }
        }
    }

    fun TypeRuntimeWiring.Builder.querySaker(
        altinn: Altinn,
        altinnRolleService: AltinnRolleService,
        brukerRepository: BrukerRepository
    ) {
        coDataFetcher("saker") { env ->
            val context = env.getContext<Context>()
            val virksomhetsnummer = env.getArgument<String>("virksomhetsnummer")
            coroutineScope {
                val tilganger = async {
                    try {
                        altinn.hentTilganger(
                            context.fnr,
                            context.token,
                            MottakerRegister.servicecodeDefinisjoner,
                            altinnRolleService.hentRoller(MottakerRegister.rolleDefinisjoner),
                        )
                    } catch (e: AltinnrettigheterProxyKlientFallbackException) {
                        if (e.erDriftsforstyrrelse())
                            log.info("Henting av Altinn-tilganger feilet", e)
                        else
                            log.error("Henting av Altinn-tilganger feilet", e)
                        null
                    } catch (e: Exception) {
                        log.error("Henting av Altinn-tilganger feilet", e)
                        null
                    }
                }
                val saker = brukerRepository.hentSaker(context.fnr, virksomhetsnummer, tilganger.await().orEmpty())
                    .map {
                        Sak(
                            id = it.sakId.toString(),
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

                SakerResultat(
                    saker = saker,
                    feilAltinn = tilganger.await() == null
                )
            }
        }
    }

    fun TypeRuntimeWiring.Builder.mutationBrukerKlikketPa(
        brukerRepository: BrukerRepository,
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    ) {
        coDataFetcher("notifikasjonKlikketPaa") { env ->
            val context = env.getContext<Context>()
            val notifikasjonsid = env.getTypedArgument<UUID>("id")

            val virksomhetsnummer = brukerRepository.virksomhetsnummerForNotifikasjon(notifikasjonsid)
                ?: return@coDataFetcher UgyldigId("")

            val hendelse = Hendelse.BrukerKlikket(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = notifikasjonsid,
                fnr = context.fnr,
                virksomhetsnummer = virksomhetsnummer,
                kildeAppNavn =  naisClientId
            )

            kafkaProducer.sendHendelse(hendelse)

            brukerRepository.oppdaterModellEtterHendelse(hendelse)

            BrukerKlikk(
                id = "${context.fnr}-${hendelse.notifikasjonId}",
                klikketPaa = true
            )
        }
    }

    suspend fun <T : WithVirksomhet> fetchVirksomhet(
        enhetsregisteret: Enhetsregisteret,
        env: DataFetchingEnvironment
    ): Virksomhet {
        val source = env.getSource<T>()
        return if (env.selectionSet.contains("Virksomhet.navn")) {
            enhetsregisteret.hentUnderenhet(source.virksomhet.virksomhetsnummer).let { enhet ->
                Virksomhet(
                    virksomhetsnummer = enhet.organisasjonsnummer,
                    navn = enhet.navn
                )
            }
        } else {
            source.virksomhet
        }
    }
}