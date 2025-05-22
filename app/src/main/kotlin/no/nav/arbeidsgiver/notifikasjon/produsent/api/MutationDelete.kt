package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationDelete(
    val hendelseDispatcher: HendelseDispatcher,
    val produsentRepository: ProdusentRepository
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<SoftDeleteSakResultat>()
        runtime.resolveSubtypes<SoftDeleteNotifikasjonResultat>()
        runtime.resolveSubtypes<HardDeleteSakResultat>()
        runtime.resolveSubtypes<HardDeleteNotifikasjonResultat>()


        runtime.wire("Mutation") {
            coDataFetcher("softDeleteSak") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val sak = hentSak(
                    produsentRepository, id = env.getTypedArgument<UUID>("id")
                ) { error -> return@coDataFetcher error }

                val id = deleteSak(
                    sak = sak,
                    produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                SoftDeleteSakVellykket(id)
            }

            coDataFetcher("softDeleteSakByGrupperingsid") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val sak = hentSak(
                    produsentRepository, grupperingsid = env.getTypedArgument<String>("grupperingsid"),
                    merkelapp = env.getTypedArgument<String>("merkelapp")
                ) { error -> return@coDataFetcher error }

                val id = deleteSak(
                    sak = sak,
                    produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                SoftDeleteSakVellykket(id)
            }

            coDataFetcher("hardDeleteSak") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val sak = hentSak(
                    produsentRepository, id = env.getTypedArgument<UUID>("id")
                ) { error -> return@coDataFetcher error }

                val id = deleteSak(
                    sak = sak,
                    produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                HardDeleteSakVellykket(id)
            }

            coDataFetcher("hardDeleteSakByGrupperingsid") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val sak = hentSak(
                    produsentRepository, grupperingsid = env.getTypedArgument<String>("grupperingsid"),
                    merkelapp = env.getTypedArgument<String>("merkelapp")
                ) { error -> return@coDataFetcher error }

                val id = deleteSak(
                    sak = sak,
                    produsent = tilgangsstyrProdusent(context, sak.merkelapp) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                HardDeleteSakVellykket(id)
            }

            coDataFetcher("softDeleteNotifikasjon") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val notifikasjon = hentNotifikasjon(
                    produsentRepository, id = env.getTypedArgument<UUID>("id")
                ) { error -> return@coDataFetcher error }

                val id = deleteNotifikasjon(
                    notifikasjon = notifikasjon,
                    produsent = tilgangsstyrProdusent(
                        context,
                        notifikasjon.merkelapp
                    ) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                SoftDeleteNotifikasjonVellykket(id)
            }

            coDataFetcher("softDeleteNotifikasjonByEksternId") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val notifikasjon = hentNotifikasjon(
                    produsentRepository, eksternId = env.getTypedArgument<String>("eksternId"),
                    merkelapp = env.getTypedArgument<String>("merkelapp")
                ) { error -> return@coDataFetcher error }

                val id = deleteNotifikasjon(
                    notifikasjon = notifikasjon,
                    produsent = tilgangsstyrProdusent(
                        context,
                        notifikasjon.merkelapp
                    ) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                SoftDeleteNotifikasjonVellykket(id)
            }

            coDataFetcher("softDeleteNotifikasjonByEksternId_V2") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val notifikasjon = hentNotifikasjon(
                    produsentRepository, eksternId = env.getTypedArgument<String>("eksternId"),
                    merkelapp = env.getTypedArgument<String>("merkelapp")
                ) { error -> return@coDataFetcher error }

                val id = deleteNotifikasjon(
                    notifikasjon = notifikasjon,
                    produsent = tilgangsstyrProdusent(
                        context,
                        notifikasjon.merkelapp
                    ) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                SoftDeleteNotifikasjonVellykket(id)
            }

            coDataFetcher("hardDeleteNotifikasjon") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val notifikasjon = hentNotifikasjon(
                    produsentRepository, id = env.getTypedArgument<UUID>("id")
                ) { error -> return@coDataFetcher error }

                val id = deleteNotifikasjon(
                    notifikasjon = notifikasjon,
                    produsent = tilgangsstyrProdusent(
                        context,
                        notifikasjon.merkelapp
                    ) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                HardDeleteNotifikasjonVellykket(id)
            }

            coDataFetcher("hardDeleteNotifikasjonByEksternId") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val notifikasjon = hentNotifikasjon(
                    produsentRepository, eksternId = env.getTypedArgument<String>("eksternId"),
                    merkelapp = env.getTypedArgument<String>("merkelapp")
                ) { error -> return@coDataFetcher error }

                val id = deleteNotifikasjon(
                    notifikasjon = notifikasjon,
                    produsent = tilgangsstyrProdusent(
                        context,
                        notifikasjon.merkelapp
                    ) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                HardDeleteNotifikasjonVellykket(id)
            }

            coDataFetcher("hardDeleteNotifikasjonByEksternId_V2") { env ->
                val context = env.notifikasjonContext<ProdusentAPI.Context>()

                val notifikasjon = hentNotifikasjon(
                    produsentRepository, eksternId = env.getTypedArgument<String>("eksternId"),
                    merkelapp = env.getTypedArgument<String>("merkelapp")
                ) { error -> return@coDataFetcher error }

                val id = deleteNotifikasjon(
                    notifikasjon = notifikasjon,
                    produsent = tilgangsstyrProdusent(
                        context,
                        notifikasjon.merkelapp
                    ) { error -> return@coDataFetcher error },
                    kildeAppNavn = context.appName
                )

                HardDeleteNotifikasjonVellykket(id)
            }
        }
    }

    private suspend fun deleteSak(
        sak: ProdusentModel.Sak,
        produsent: Produsent,
        kildeAppNavn: String,
    ): UUID {
        hendelseDispatcher.send(
            HardDelete(
                hendelseId = UUID.randomUUID(),
                aggregateId = sak.id,
                virksomhetsnummer = sak.virksomhetsnummer,
                deletedAt = OffsetDateTime.now(),
                produsentId = produsent.id,
                kildeAppNavn = kildeAppNavn,
                grupperingsid = sak.grupperingsid,
                merkelapp = sak.merkelapp,
            )
        )
        return sak.id
    }

    private suspend fun deleteNotifikasjon(
        notifikasjon: ProdusentModel.Notifikasjon,
        produsent: Produsent,
        kildeAppNavn: String,
    ): UUID {

        hendelseDispatcher.send(
            HardDelete(
                hendelseId = UUID.randomUUID(),
                aggregateId = notifikasjon.id,
                virksomhetsnummer = notifikasjon.virksomhetsnummer,
                deletedAt = OffsetDateTime.now(),
                produsentId = produsent.id,
                kildeAppNavn = kildeAppNavn,
                grupperingsid = null,
                merkelapp = notifikasjon.merkelapp,
            )
        )
        return notifikasjon.id
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HardDeleteNotifikasjonResultat

    @JsonTypeName("HardDeleteNotifikasjonVellykket")
    data class HardDeleteNotifikasjonVellykket(
        val id: UUID
    ) : HardDeleteNotifikasjonResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HardDeleteSakResultat

    @JsonTypeName("HardDeleteSakVellykket")
    data class HardDeleteSakVellykket(
        val id: UUID
    ) : HardDeleteSakResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface SoftDeleteNotifikasjonResultat

    @JsonTypeName("SoftDeleteNotifikasjonVellykket")
    data class SoftDeleteNotifikasjonVellykket(
        val id: UUID
    ) : SoftDeleteNotifikasjonResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface SoftDeleteSakResultat

    @JsonTypeName("SoftDeleteSakVellykket")
    data class SoftDeleteSakVellykket(
        val id: UUID
    ) : SoftDeleteSakResultat
}
