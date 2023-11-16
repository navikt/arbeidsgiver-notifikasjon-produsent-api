package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import io.micrometer.core.instrument.Counter
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationOppgaveUtfoert(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    private val oppgaveUtfoertByEksternIdCalls = Counter.builder("graphql.mutation")
        .tag("field", "oppgaveUtfoertByEksternId")
        .register(Metrics.meterRegistry)

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<OppgaveUtfoertResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("oppgaveUtfoert") { env ->
                oppgaveUtført(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id"),
                    hardDelete = env.getTypedArgumentOrNull<HardDeleteUpdateInput>("hardDelete"),
                    nyLenke = env.getTypedArgumentOrNull("nyLenke"),
                    utfoertTidspunkt = env.getTypedArgumentOrNull<OffsetDateTime>("utfoertTidspunkt"),
                )
            }
            coDataFetcher("oppgaveUtfoertByEksternId") { env ->
                oppgaveUtfoertByEksternIdCalls.increment()
                oppgaveUtfoertByEksternId(env)
            }
            coDataFetcher("oppgaveUtfoertByEksternId_V2") { env ->
                oppgaveUtfoertByEksternId(env)
            }
        }
    }

    private suspend fun oppgaveUtfoertByEksternId(env: DataFetchingEnvironment) =
        oppgaveUtført(
            context = env.notifikasjonContext(),
            eksternId = env.getTypedArgument("eksternId"),
            merkelapp = env.getTypedArgument("merkelapp"),
            hardDelete = env.getTypedArgumentOrNull("hardDelete"),
            nyLenke = env.getTypedArgumentOrNull("nyLenke"),
            utfoertTidspunkt = env.getTypedArgumentOrNull<OffsetDateTime>("utfoertTidspunkt"),
        )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface OppgaveUtfoertResultat

    @JsonTypeName("OppgaveUtfoertVellykket")
    data class OppgaveUtfoertVellykket(
        val id: UUID
    ) : OppgaveUtfoertResultat

    private suspend fun oppgaveUtført(
        context: ProdusentAPI.Context,
        id: UUID,
        hardDelete: HardDeleteUpdateInput?,
        nyLenke: String?,
        utfoertTidspunkt: OffsetDateTime?,
    ): OppgaveUtfoertResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, id) { error -> return error }
        return oppgaveUtført(context, notifikasjon, hardDelete, nyLenke, utfoertTidspunkt)
    }

    private suspend fun oppgaveUtført(
        context: ProdusentAPI.Context,
        eksternId: String,
        merkelapp: String,
        hardDelete: HardDeleteUpdateInput?,
        nyLenke: String?,
        utfoertTidspunkt: OffsetDateTime?,
    ): OppgaveUtfoertResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, eksternId, merkelapp) { error -> return error }
        return oppgaveUtført(context, notifikasjon, hardDelete, nyLenke, utfoertTidspunkt)
    }

    private suspend fun oppgaveUtført(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
        hardDelete: HardDeleteUpdateInput?,
        nyLenke: String?,
        utfoertTidspunkt: OffsetDateTime?,
    ): OppgaveUtfoertResultat {

        if (notifikasjon !is ProdusentModel.Oppgave) {
            return Error.NotifikasjonFinnesIkke("Notifikasjonen (id ${notifikasjon.id}) er ikke en oppgave")
        }

        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

        val utførtHendelse = OppgaveUtført(
            hendelseId = UUID.randomUUID(),
            notifikasjonId = notifikasjon.id,
            virksomhetsnummer = notifikasjon.virksomhetsnummer,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
            hardDelete = hardDelete?.tilDomene(),
            nyLenke = nyLenke,
            utfoertTidspunkt = utfoertTidspunkt ?: OffsetDateTime.now(),
        )

        hendelseDispatcher.send(utførtHendelse)
        return OppgaveUtfoertVellykket(notifikasjon.id)
    }
}