package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.Companion.tilBrukerAPI
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import java.time.OffsetDateTime
import java.util.*

object BrukerAPI {
    private val log = logger()

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
        enhetsregisteret: Enhetsregisteret,
        brukerModel: BrukerModel,
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
        nærmesteLederService: NærmesteLederService,
    ) = TypedGraphQL<Context>(
        createGraphQL("/bruker.graphqls") {

            scalar(Scalars.ISO8601DateTime)

            resolveSubtypes<Notifikasjon>()
            resolveSubtypes<NotifikasjonKlikketPaaResultat>()

            wire("Query") {
                dataFetcher("whoami") {
                    it.getContext<Context>().fnr
                }

                queryNotifikasjoner(
                    altinn = altinn,
                    nærmesteLederService = nærmesteLederService,
                    brukerModel = brukerModel
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
            }

            wire("Mutation") {
                mutationBrukerKlikketPa(
                    brukerModel = brukerModel,
                    kafkaProducer = kafkaProducer,
                )
            }
        }
    )

    fun TypeRuntimeWiring.Builder.queryNotifikasjoner(
        altinn: Altinn,
        nærmesteLederService: NærmesteLederService,
        brukerModel: BrukerModel
    ) {
        coDataFetcher("notifikasjoner") { env ->
            val context = env.getContext<Context>()
            coroutineScope {
                val tilganger = async {
                    try {
                        altinn.hentAlleTilganger(context.fnr, context.token)
                    } catch (e: Exception) {
                        log.error("Henting av Altinn-tilganger feilet", e)
                        null
                    }

                }
                val ansatte = async {
                    try{
                        nærmesteLederService.hentAnsatte(context.token)
                    } catch (e: Exception) {
                        log.error("Henting av DigiSyfo-tilganger feilet", e)
                        null
                    }
                }

                val notifikasjoner = brukerModel
                    .hentNotifikasjoner(
                        context.fnr,
                        tilganger.await().orEmpty(),
                        ansatte.await().orEmpty())
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
                                        when (notifikasjon.mottaker) {
                                            is NærmesteLederMottaker -> notifikasjon.mottaker.virksomhetsnummer
                                            is AltinnMottaker -> notifikasjon.mottaker.virksomhetsnummer
                                        }
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
                                        when (notifikasjon.mottaker) {
                                            is NærmesteLederMottaker -> notifikasjon.mottaker.virksomhetsnummer
                                            is AltinnMottaker -> notifikasjon.mottaker.virksomhetsnummer
                                        }
                                    ),
                                    brukerKlikk = BrukerKlikk(
                                        id = "${context.fnr}-${notifikasjon.id}",
                                        klikketPaa = notifikasjon.klikketPaa
                                    )
                                )
                        }
                    }
                return@coroutineScope NotifikasjonerResultat(
                    notifikasjoner,
                    feilAltinn = tilganger.await() == null,
                    feilDigiSyfo = ansatte.await() == null
                )
            }
        }
    }

    fun TypeRuntimeWiring.Builder.mutationBrukerKlikketPa(
        brukerModel: BrukerModel,
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    ) {
        coDataFetcher("notifikasjonKlikketPaa") { env ->
            val context = env.getContext<Context>()
            val notifikasjonsid = env.getTypedArgument<UUID>("id")

            val virksomhetsnummer = brukerModel.virksomhetsnummerForNotifikasjon(notifikasjonsid)
                ?: return@coDataFetcher UgyldigId("")

            val hendelse = Hendelse.BrukerKlikket(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = notifikasjonsid,
                fnr = context.fnr,
                virksomhetsnummer = virksomhetsnummer
            )

            kafkaProducer.sendHendelse(hendelse)

            brukerModel.oppdaterModellEtterHendelse(hendelse)

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
            enhetsregisteret.hentEnhet(source.virksomhet.virksomhetsnummer).let { enhet ->
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