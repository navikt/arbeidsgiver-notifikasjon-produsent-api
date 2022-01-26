package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.virksomhetsnummer
import java.util.*

class MutationOppgaveUtfoert(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<OppgaveUtfoertResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("oppgaveUtfoert") { env ->
                oppgaveUtført(
                    context = env.getContext(),
                    id = env.getTypedArgument("id"),
                )
            }
            coDataFetcher("oppgaveUtfoertByEksternId") { env ->
                oppgaveUtført(
                    context = env.getContext(),
                    eksternId = env.getTypedArgument("eksternId"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface OppgaveUtfoertResultat

    @JsonTypeName("OppgaveUtfoertVellykket")
    data class OppgaveUtfoertVellykket(
        val id: UUID
    ) : OppgaveUtfoertResultat

    private suspend fun oppgaveUtført(
        context: ProdusentAPI.Context,
        id: UUID,
    ): OppgaveUtfoertResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, id) { error -> return error }
        return oppgaveUtført(context, notifikasjon)
    }

    private suspend fun oppgaveUtført(
        context: ProdusentAPI.Context,
        eksternId: String,
        merkelapp: String,
    ): OppgaveUtfoertResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, eksternId, merkelapp) { error -> return error }
        return oppgaveUtført(context, notifikasjon)
    }

    private suspend fun oppgaveUtført(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
    ): OppgaveUtfoertResultat {

        if (notifikasjon !is ProdusentModel.Oppgave) {
            return Error.NotifikasjonFinnesIkke("Notifikasjonen (id ${notifikasjon.id}) er ikke en oppgave")
        }

        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

        val utførtHendelse = Hendelse.OppgaveUtført(
            hendelseId = UUID.randomUUID(),
            notifikasjonId = notifikasjon.id,
            virksomhetsnummer = notifikasjon.virksomhetsnummer,
            produsentId = produsent.id,
            kildeAppNavn = context.appName
        )

        kafkaProducer.sendHendelse(utførtHendelse)
        produsentRepository.oppdaterModellEtterHendelse(utførtHendelse)
        return OppgaveUtfoertVellykket(notifikasjon.id)
    }
}