package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import io.micrometer.core.instrument.Counter
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationOppgaveUtgaatt(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    private val oppgaveUtgaattByEksternIdCalls = Counter.builder("graphql.mutation")
        .tag("field", "oppgaveUtgaattByEksternId")
        .register(Metrics.meterRegistry)

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<OppgaveUtgaattResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("oppgaveUtgaatt") { env ->
                oppgaveUtgått(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id"),
                    hardDelete = env.getTypedArgumentOrNull<HardDeleteUpdateInput>("hardDelete"),
                    nyLenke = env.getTypedArgumentOrNull("nyLenke"),
                    utgaattTidspunkt = env.getTypedArgumentOrNull<OffsetDateTime>("utgaattTidspunkt"),
                )
            }
            coDataFetcher("oppgaveUtgaattByEksternId") { env ->
                oppgaveUtgaattByEksternIdCalls.increment()
                oppgaveUtgaattByEksternId(env)
            }
        }
    }

    private suspend fun oppgaveUtgaattByEksternId(env: DataFetchingEnvironment) =
        oppgaveUtgått(
            context = env.notifikasjonContext(),
            eksternId = env.getTypedArgument("eksternId"),
            merkelapp = env.getTypedArgument("merkelapp"),
            hardDelete = env.getTypedArgumentOrNull("hardDelete"),
            nyLenke = env.getTypedArgumentOrNull("nyLenke"),
            utgaattTidspunkt = env.getTypedArgumentOrNull<OffsetDateTime>("utgaattTidspunkt"),
        )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface OppgaveUtgaattResultat

    @JsonTypeName("OppgaveUtgaattVellykket")
    data class OppgaveUtgaattVellykket(
        val id: UUID
    ) : OppgaveUtgaattResultat

    private suspend fun oppgaveUtgått(
        context: ProdusentAPI.Context,
        id: UUID,
        hardDelete: HardDeleteUpdateInput?,
        nyLenke: String?,
        utgaattTidspunkt: OffsetDateTime?,
    ): OppgaveUtgaattResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, id) { error -> return error }
        return oppgaveUtgått(context, notifikasjon, hardDelete, nyLenke, utgaattTidspunkt)
    }

    private suspend fun oppgaveUtgått(
        context: ProdusentAPI.Context,
        eksternId: String,
        merkelapp: String,
        hardDelete: HardDeleteUpdateInput?,
        nyLenke: String?,
        utgaattTidspunkt: OffsetDateTime?,
    ): OppgaveUtgaattResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, eksternId, merkelapp) { error -> return error }
        return oppgaveUtgått(context, notifikasjon, hardDelete, nyLenke, utgaattTidspunkt)
    }

    private suspend fun oppgaveUtgått(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
        hardDelete: HardDeleteUpdateInput?,
        nyLenke: String?,
        utgaattTidspunkt: OffsetDateTime?,
    ): OppgaveUtgaattResultat {

        if (notifikasjon !is ProdusentModel.Oppgave) {
            return Error.NotifikasjonFinnesIkke("Notifikasjonen (id ${notifikasjon.id}) er ikke en oppgave")
        }

        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

        if (notifikasjon.tilstand == ProdusentModel.Oppgave.Tilstand.UTFOERT) {
            return Error.OppgavenErAlleredeUtfoert("Oppgaven er allerede utført")
        }

        val utgåttHendelse = OppgaveUtgått(
            hendelseId = UUID.randomUUID(),
            notifikasjonId = notifikasjon.id,
            virksomhetsnummer = notifikasjon.virksomhetsnummer,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
            hardDelete = hardDelete?.tilHendelseModel(),
            utgaattTidspunkt = utgaattTidspunkt ?: OffsetDateTime.now(),
            nyLenke = nyLenke,
        )

        hendelseDispatcher.send(utgåttHendelse)
        return OppgaveUtgaattVellykket(notifikasjon.id)
    }
}