package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDeleteSakOppdatert
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationSkedulerHardDeleteSak(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<SkedulerHardDeleteSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("skedulerHardDeleteSak") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val sak = hentSak(
                    produsentRepository, id = env.getTypedArgument<UUID>("id")
                ) { error -> return@coDataFetcher error }

                skedulerHardDelete(
                    sak = sak,
                    produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName,
                    hardDelete = env.getTypedArgument<HardDeleteUpdateInput>("hardDelete"),
                )
            }

            coDataFetcher("skedulerHardDeleteSakByGrupperingsid") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val sak = hentSak(
                    produsentRepository,
                    grupperingsid = env.getTypedArgument<String>("grupperingsid"),
                    merkelapp = env.getTypedArgument<String>("merkelapp"),
                ) { error -> return@coDataFetcher error }

                skedulerHardDelete(
                    sak = sak,
                    produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName,
                    hardDelete = env.getTypedArgument<HardDeleteUpdateInput>("hardDelete"),
                )
            }
        }
    }

    private suspend fun skedulerHardDelete(
        sak: ProdusentModel.Sak,
        produsent: Produsent,
        kildeAppNavn: String,
        hardDelete: HardDeleteUpdateInput,
    ): SkedulerHardDeleteSakVellykket {
        hendelseDispatcher.send(
            HardDeleteSakOppdatert(
                hendelseId = UUID.randomUUID(),
                virksomhetsnummer = sak.virksomhetsnummer,
                produsentId = produsent.id,
                kildeAppNavn = kildeAppNavn,
                sakId = sak.id,
                merkelapp = sak.merkelapp,
                grupperingsid = sak.grupperingsid,
                mottattTidspunkt = OffsetDateTime.now(),
                hardDelete = hardDelete.tilHendelseModel(),
            )
        )
        return SkedulerHardDeleteSakVellykket(sak.id)
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface SkedulerHardDeleteSakResultat

    @JsonTypeName("SkedulerHardDeleteSakVellykket")
    data class SkedulerHardDeleteSakVellykket(
        val id: UUID
    ) : SkedulerHardDeleteSakResultat
}
