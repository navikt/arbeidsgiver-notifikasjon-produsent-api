package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationSoftDeleteNotifikasjon(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<SoftDeleteNotifikasjonResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("softDeleteNotifikasjon") { env ->
                softDelete(
                    context = env.getContext(),
                    id = env.getTypedArgument("id")
                )
            }
            coDataFetcher("softDeleteNotifikasjonByEksternId") { env ->
                softDelete(
                    context = env.getContext(),
                    eksternId = env.getTypedArgument("eksternId"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
            coDataFetcher("softDeleteNotifikasjonByEksternId_V2") { env ->
                softDelete(
                    context = env.getContext(),
                    eksternId = env.getTypedArgument("eksternId"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface SoftDeleteNotifikasjonResultat

    @JsonTypeName("SoftDeleteNotifikasjonVellykket")
    data class SoftDeleteNotifikasjonVellykket(
        val id: UUID
    ) : SoftDeleteNotifikasjonResultat

    private suspend fun softDelete(
        context: ProdusentAPI.Context,
        id: UUID,
    ): SoftDeleteNotifikasjonResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, id) { error -> return error }
        return softDelete(context, notifikasjon)
    }

    private suspend fun softDelete(
        context: ProdusentAPI.Context,
        eksternId: String,
        merkelapp: String,
    ): SoftDeleteNotifikasjonResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, eksternId, merkelapp) { error -> return error }
        return softDelete(context, notifikasjon)
    }

    private suspend fun softDelete(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
    ): SoftDeleteNotifikasjonResultat {
        val produsent = tilgangsstyrProdusent(context, notifikasjon.merkelapp) { error -> return error }

        val softDelete = SoftDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = notifikasjon.id,
            virksomhetsnummer = notifikasjon.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            produsentId = produsent.id,
            kildeAppNavn = context.appName
        )

        hendelseDispatcher.send(softDelete)
        return SoftDeleteNotifikasjonVellykket(notifikasjon.id)
    }
}
