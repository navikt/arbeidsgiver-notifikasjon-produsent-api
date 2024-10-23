package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat
import java.time.OffsetDateTime
import java.util.*

internal class MutationPaaminnelse(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<OppgaveUtsettFristResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("oppgaveEndrePaaminnelse") { env ->
                oppgaveEndrePaaminnelse(
                    context = env.notifikasjonContext(),
                    notifikasjon = hentNotifikasjon(
                        produsentRepository,
                        id = env.getTypedArgument<UUID>("id")
                    ) { error -> return@coDataFetcher error },
                    paaminnelse = env.getTypedArgumentOrNull<PaaminnelseInput>("paaminnelse"),
                )
            }
            coDataFetcher("oppgaveEndrePaaminnelseByEksternId") { env ->
                oppgaveEndrePaaminnelse(
                    context = env.notifikasjonContext(),
                    notifikasjon = hentNotifikasjon(
                        produsentRepository,
                        eksternId = env.getTypedArgument<String>("eksternId"),
                        merkelapp = env.getTypedArgument<String>("merkelapp")
                    ) { error -> return@coDataFetcher error },
                    paaminnelse = env.getTypedArgumentOrNull<PaaminnelseInput>("paaminnelse"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface OppgaveEndrePaaminnelseResultat

    @JsonTypeName("OppgaveEndrePaaminnelseVellykket")
    data class OppgaveEndrePaaminnelseVellykket(
        val id: UUID
    ) : OppgaveEndrePaaminnelseResultat

    private suspend fun oppgaveEndrePaaminnelse( //TODO: påminnelse på kalenderavtale også?
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
        paaminnelse: PaaminnelseInput?,
    ) : OppgaveEndrePaaminnelseResultat {
        if (notifikasjon !is ProdusentModel.Oppgave) {
            return Error.NotifikasjonFinnesIkke("Notifikasjonen (id ${notifikasjon.id}) er ikke en oppgave")
        }

        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }
        try {
            hendelseDispatcher.send(
                HendelseModel.EndrePaaminnelse(
                    hendelseId = UUID.randomUUID(),
                    notifikasjonId = notifikasjon.id,
                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                    produsentId = produsent.id,
                    kildeAppNavn = context.appName,
                    påminnelse = paaminnelse?.tilDomene(
                        opprettetTidspunkt = notifikasjon.opprettetTidspunkt, //TODO: skal dette være etter oppgaven ble opprettet, eller etter nå?
                        frist = notifikasjon.frist, //TODO: sjekk denne
                        startTidspunkt = null, // endre dersom kalenderavtale?
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                    )
                )
            )
        }
        catch (e: UgyldigPåminnelseTidspunktException) { // Er dette riktig?
            return Error.UgyldigPåminnelseTidspunkt(e.message!!)
        }

        return OppgaveEndrePaaminnelseVellykket(notifikasjon.id)
    }
}