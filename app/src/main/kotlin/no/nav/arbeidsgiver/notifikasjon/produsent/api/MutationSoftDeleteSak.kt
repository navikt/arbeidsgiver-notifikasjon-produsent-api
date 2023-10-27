package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationSoftDeleteSak(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<SoftDeleteSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("softDeleteSak") { env ->
                softDelete(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id")
                )
            }
            coDataFetcher("softDeleteSakByGrupperingsid") { env ->
                softDelete(
                    context = env.notifikasjonContext(),
                    grupperingsid = env.getTypedArgument("grupperingsid"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface SoftDeleteSakResultat

    @JsonTypeName("SoftDeleteSakVellykket")
    data class SoftDeleteSakVellykket(
        val id: UUID
    ) : SoftDeleteSakResultat

    private suspend fun softDelete(
        context: ProdusentAPI.Context,
        id: UUID,
    ): SoftDeleteSakResultat {
        val sak = hentSak(produsentRepository, id) { error -> return error }
        return softDelete(context, sak)
    }

    private suspend fun softDelete(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String,
    ): SoftDeleteSakResultat {
        val sak = hentSak(produsentRepository, grupperingsid, merkelapp) { error -> return error }
        return softDelete(context, sak)
    }

    private suspend fun softDelete(
        context: ProdusentAPI.Context,
        sak: ProdusentModel.Sak,
    ): SoftDeleteSakResultat {
        val produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return error }

        val softDelete = SoftDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = sak.id,
            virksomhetsnummer = sak.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
            grupperingsid = sak.grupperingsid,
            merkelapp = sak.merkelapp,
        )

        hendelseDispatcher.send(softDelete)
        return SoftDeleteSakVellykket(sak.id)
    }
}
