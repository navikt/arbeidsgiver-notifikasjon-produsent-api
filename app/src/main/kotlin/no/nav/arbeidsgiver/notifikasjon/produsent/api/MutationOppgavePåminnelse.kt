package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationOppgavePåminnelse(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<OppgaveEndrePaaminnelseResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("oppgaveEndrePaaminnelse") { env ->
                oppgaveEndrePaaminnelse(
                    context = env.notifikasjonContext(),
                    idempotenceKey = env.getTypedArgumentOrNull<String>("idempotencyKey"),
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
                    idempotenceKey = env.getTypedArgumentOrNull<String>("idempotencyKey"),
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

    private suspend fun oppgaveEndrePaaminnelse(
        context: ProdusentAPI.Context,
        idempotenceKey: String?,
        notifikasjon: ProdusentModel.Notifikasjon,
        paaminnelse: PaaminnelseInput?,
    ) : OppgaveEndrePaaminnelseResultat {
        if (notifikasjon !is ProdusentModel.Oppgave) {
            return Error.NotifikasjonFinnesIkke("Notifikasjonen (id ${notifikasjon.id}) er ikke en oppgave")
        }

        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

        idempotenceKey?.let {
            if (produsentRepository.notifikasjonOppdateringFinnes(notifikasjon.id, it)) {
                return OppgaveEndrePaaminnelseVellykket(notifikasjon.id)
            }
        }

        try {
            hendelseDispatcher.send(
                HendelseModel.OppgavePåminnelseEndret(
                    hendelseId = UUID.randomUUID(),
                    notifikasjonId = notifikasjon.id,
                    merkelapp = notifikasjon.merkelapp,
                    virksomhetsnummer = notifikasjon.virksomhetsnummer,
                    produsentId = produsent.id,
                    kildeAppNavn = context.appName,
                    påminnelse = paaminnelse?.tilDomene(
                        opprettetTidspunkt = notifikasjon.opprettetTidspunkt,
                        frist = notifikasjon.frist,
                        startTidspunkt = null,
                        virksomhetsnummer = notifikasjon.virksomhetsnummer,
                    ),
                    frist = notifikasjon.frist,
                    oppgaveOpprettetTidspunkt = notifikasjon.opprettetTidspunkt.toInstant(),
                    idempotenceKey = idempotenceKey
                )
            )
        } catch (e: UgyldigPåminnelseTidspunktException) {
            return Error.UgyldigPåminnelseTidspunkt(e.message!!)
        }

        return OppgaveEndrePaaminnelseVellykket(notifikasjon.id)
    }
}