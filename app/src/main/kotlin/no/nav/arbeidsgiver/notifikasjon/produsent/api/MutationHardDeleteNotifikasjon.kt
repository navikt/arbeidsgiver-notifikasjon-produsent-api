package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.notifikasjonContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationHardDeleteNotifikasjon(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<HardDeleteNotifikasjonResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("hardDeleteNotifikasjon") { env ->
                hardDelete(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id")
                )
            }
            coDataFetcher("hardDeleteNotifikasjonByEksternId") { env ->
                hardDelete(
                    context = env.notifikasjonContext(),
                    eksternId = env.getTypedArgument("eksternId"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }

            coDataFetcher("hardDeleteNotifikasjonByEksternId_V2") { env ->
                hardDelete(
                    context = env.notifikasjonContext(),
                    eksternId = env.getTypedArgument("eksternId"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HardDeleteNotifikasjonResultat

    @JsonTypeName("HardDeleteNotifikasjonVellykket")
    data class HardDeleteNotifikasjonVellykket(
        val id: UUID
    ) : HardDeleteNotifikasjonResultat

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        id: UUID,
    ): HardDeleteNotifikasjonResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, id) { error -> return error }
        return hardDelete(context, notifikasjon)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        eksternId: String,
        merkelapp: String,
    ): HardDeleteNotifikasjonResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, eksternId, merkelapp) { error -> return error }
        return hardDelete(context, notifikasjon)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
    ): HardDeleteNotifikasjonResultat {
        val produsent = tilgangsstyrProdusent(context, notifikasjon.merkelapp) { error -> return error }

        val hardDelete = HardDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = notifikasjon.id,
            virksomhetsnummer = notifikasjon.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            produsentId = produsent.id,
            kildeAppNavn = context.appName
        )
        hendelseDispatcher.send(hardDelete)
        return HardDeleteNotifikasjonVellykket(notifikasjon.id)
    }
}