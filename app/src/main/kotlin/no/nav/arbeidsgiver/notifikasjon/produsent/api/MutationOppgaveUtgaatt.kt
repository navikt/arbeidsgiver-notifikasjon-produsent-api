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
                    context = env.getContext(),
                    id = env.getTypedArgument("id"),
                    hardDelete = env.getTypedArgumentOrNull<HardDeleteUpdateInput>("hardDelete"),
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
            context = env.getContext(),
            eksternId = env.getTypedArgument("eksternId"),
            merkelapp = env.getTypedArgument("merkelapp"),
            hardDelete = env.getTypedArgumentOrNull("hardDelete")
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
    ): OppgaveUtgaattResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, id) { error -> return error }
        return oppgaveUtgått(context, notifikasjon, hardDelete)
    }

    private suspend fun oppgaveUtgått(
        context: ProdusentAPI.Context,
        eksternId: String,
        merkelapp: String,
        hardDelete: HardDeleteUpdateInput?,
    ): OppgaveUtgaattResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, eksternId, merkelapp) { error -> return error }
        return oppgaveUtgått(context, notifikasjon, hardDelete)
    }

    private suspend fun oppgaveUtgått(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
        hardDelete: HardDeleteUpdateInput?,
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
            hardDelete = hardDelete?.tilDomene()
        )

        hendelseDispatcher.send(utgåttHendelse)
        return OppgaveUtgaattVellykket(notifikasjon.id)
    }
}