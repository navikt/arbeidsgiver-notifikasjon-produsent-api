package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.apache.kafka.clients.producer.Producer
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

object BrukerAPI {
    private val log = logger()

    data class Context(
        val fnr: String,
        val token: String,
        override val coroutineScope: CoroutineScope
    ): WithCoroutineScope

    sealed interface Klikkbar {
        val klikketPaa: Boolean
    }

    sealed class Notifikasjon {
        data class Beskjed(
            val merkelapp: String,
            val tekst: String,
            val lenke: String,
            val opprettetTidspunkt: OffsetDateTime,
            val uuid: UUID,
            override val klikketPaa: Boolean
        ) : Notifikasjon(), Klikkbar
    }

    data class NotifikasjonKlikketPaaResultat(
        val errors: List<MutationError>
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class MutationError {
        abstract val feilmelding: String

        @JsonTypeName("UgyldigId")
        data class UgyldigId(
            override val feilmelding: String
        ) : MutationError()
    }

    fun createBrukerGraphQL(
        altinn: Altinn,
        queryModelFuture: CompletableFuture<QueryModel>,
        kafkaProducer: CoroutineProducer<KafkaKey, Hendelse>
    ) = TypedGraphQL<Context>(
        createGraphQL("/bruker.graphqls") {
            scalar(Scalars.ISO8601DateTime)

            resolveSubtypes<Notifikasjon>()
            resolveSubtypes<Klikkbar>()
            resolveSubtypes<MutationError>()

            wire("Query") {
                dataFetcher("ping") {
                    "pong"
                }

                coDataFetcher("notifikasjoner") { env ->
                    val context = env.getContext<Context>()
                    val tilganger = altinn.hentAlleTilganger(context.fnr, context.token)

                    return@coDataFetcher queryModelFuture.await()
                        .hentNotifikasjoner(context.fnr, tilganger)
                        .map { queryBeskjed ->
                            Notifikasjon.Beskjed(
                                merkelapp = queryBeskjed.merkelapp,
                                tekst = queryBeskjed.tekst,
                                lenke = queryBeskjed.lenke,
                                opprettetTidspunkt = queryBeskjed.opprettetTidspunkt,
                                uuid = queryBeskjed.uuid,
                                klikketPaa = queryBeskjed.klikketPaa
                            )
                        }
                }

                dataFetcher("whoami") {
                    it.getContext<Context>().fnr
                }
            }

            wire("Mutation") {
                coDataFetcher("notifikasjonKlikketPaa") { env ->
                    val context = env.getContext<Context>()
                    val notifikasjonsid = env.getTypedArgument<UUID>("uuid")
                    val queryModel = queryModelFuture.await()

                    val virksomhetsnummer = queryModel.virksomhetsnummerForNotifikasjon(notifikasjonsid)
                        ?: return@coDataFetcher NotifikasjonKlikketPaaResultat(
                            errors = listOf(MutationError.UgyldigId(""))
                        )

                    val hendelse = Hendelse.BrukerKlikket(
                        notifikasjonsId = notifikasjonsid,
                        fnr = context.fnr,
                        virksomhetsnummer = virksomhetsnummer
                    )

                    kafkaProducer.brukerKlikket(hendelse)

                    queryModel.oppdaterModellEtterBrukerKlikket(hendelse)

                    NotifikasjonKlikketPaaResultat(
                        errors = listOf()
                    )
                }
            }
        }
    )
}