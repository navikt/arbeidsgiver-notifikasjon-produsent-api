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

internal class MutationHardDeleteSak(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<HardDeleteSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("hardDeleteSak") { env ->
                hardDelete(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id")
                )
            }
            coDataFetcher("hardDeleteSakByGrupperingsid") { env ->
                hardDelete(
                    context = env.notifikasjonContext(),
                    grupperingsid = env.getTypedArgument("grupperingsid"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HardDeleteSakResultat

    @JsonTypeName("HardDeleteSakVellykket")
    data class HardDeleteSakVellykket(
        val id: UUID
    ) : HardDeleteSakResultat

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        id: UUID,
    ): HardDeleteSakResultat {
        val sak = hentSak(produsentRepository, id) { error -> return error }
        return hardDelete(context, sak)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String,
    ): HardDeleteSakResultat {
        val sak = hentSak(produsentRepository, grupperingsid, merkelapp) { error -> return error }
        return hardDelete(context, sak)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        sak: ProdusentModel.Sak,
    ): HardDeleteSakResultat {
        val produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return error }

        val hardDelete = HardDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = sak.id,
            virksomhetsnummer = sak.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            produsentId = produsent.id,
            kildeAppNavn = context.appName
        )

        hendelseDispatcher.send(hardDelete)
        return HardDeleteSakVellykket(sak.id)
    }
}