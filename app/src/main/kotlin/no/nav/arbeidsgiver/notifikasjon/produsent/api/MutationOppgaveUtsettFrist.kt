package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

internal class MutationOppgaveUtsettFrist(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<OppgaveUtsettFristResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("oppgaveUtsettFrist") { env ->
                oppgaveUtsettFrist(
                    context = env.notifikasjonContext(),
                    notifikasjon = hentNotifikasjon(
                        produsentRepository,
                        id = env.getTypedArgument<UUID>("id")
                    ) { error -> return@coDataFetcher error },
                    nyFrist = env.getTypedArgument("nyFrist"),
                    paaminnelse = env.getTypedArgumentOrNull<MutationNyOppgave.PaaminnelseInput>("paaminnelse"),
                )
            }
            coDataFetcher("oppgaveUtsettFristByEksternId") { env ->
                oppgaveUtsettFrist(
                    context = env.notifikasjonContext(),
                    notifikasjon = hentNotifikasjon(
                        produsentRepository,
                        eksternId = env.getTypedArgument<String>("eksternId"),
                        merkelapp = env.getTypedArgument<String>("merkelapp")
                    ) { error -> return@coDataFetcher error },
                    nyFrist = env.getTypedArgument("nyFrist"),
                    paaminnelse = env.getTypedArgumentOrNull<MutationNyOppgave.PaaminnelseInput>("paaminnelse"),

                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface OppgaveUtsettFristResultat

    @JsonTypeName("OppgaveUtsettFristVellykket")
    data class OppgaveUtsettFristVellykket(
        val id: UUID
    ) : OppgaveUtsettFristResultat

    private suspend fun oppgaveUtsettFrist(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
        nyFrist: LocalDate,
        paaminnelse: MutationNyOppgave.PaaminnelseInput?,
    ): OppgaveUtsettFristResultat {
        requireGraphql(NaisEnvironment.clusterName != "prod-gcp") {
            "Denne operasjonen er ikke tilgjengelig i prod enda"
        }

        if (notifikasjon !is ProdusentModel.Oppgave) {
            return Error.NotifikasjonFinnesIkke("Notifikasjonen (id ${notifikasjon.id}) er ikke en oppgave")
        }

        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

        hendelseDispatcher.send(
            FristUtsatt(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = notifikasjon.id,
                virksomhetsnummer = notifikasjon.virksomhetsnummer,
                produsentId = produsent.id,
                kildeAppNavn = context.appName,
                frist = nyFrist,
                fristEndretTidspunkt = Instant.now(),
                påminnelse = paaminnelse?.tilDomene(
                    opprettetTidspunkt = OffsetDateTime.now(),
                    frist = nyFrist,
                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                ),
            )
        )
        return OppgaveUtsettFristVellykket(notifikasjon.id)
    }
}