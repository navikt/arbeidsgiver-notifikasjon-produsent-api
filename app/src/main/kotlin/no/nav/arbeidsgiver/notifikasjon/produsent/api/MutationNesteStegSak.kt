package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.util.*

class MutationNesteStegSak(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {

    private fun DataFetchingEnvironment.getNesteSteg(): NesteStegSakInput {
        return NesteStegSakInput(
            nesteSteg = getTypedArgumentOrNull("nesteSteg"),
            idempotencyKey = getTypedArgumentOrNull("idempotencyKey"),
        )
    }

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NesteStegSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nesteStegSak") { env ->
                nesteStegSak(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id"),
                    nesteSteg = env.getNesteSteg()
                )
            }
            coDataFetcher("nesteStegSakByGrupperingsid") { env ->
                nesteStegSakByGrupperingsid(
                    context = env.notifikasjonContext(),
                    grupperingsid = env.getTypedArgument("grupperingsid"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                    nesteSteg = env.getNesteSteg()
                )
            }
        }
    }


    data class NesteStegSakInput(
        val nesteSteg: String?,
        val idempotencyKey: String?,
    ){
        fun nesteStegSakHendelse(
            kildeAppNavn: String,
            produsentId: String,
            tilhørendeSak: ProdusentModel.Sak,
            idempotencyKey: String?
        ): HendelseModel.NesteStegSak {
            return HendelseModel.NesteStegSak(
                hendelseId = UUID.randomUUID(),
                merkelapp = tilhørendeSak.merkelapp,
                grupperingsid = tilhørendeSak.grupperingsid,
                kildeAppNavn = kildeAppNavn,
                produsentId = produsentId,
                sakId = tilhørendeSak.id,
                nesteSteg = nesteSteg,
                idempotenceKey = idempotencyKey,
                virksomhetsnummer = tilhørendeSak.virksomhetsnummer,
            )
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NesteStegSakResultat

    @JsonTypeName("NesteStegSakVellykket")
    data class NesteStegSakVellykket(
        val id: UUID,
    ) : NesteStegSakResultat


    private suspend fun nesteStegSak(
        context: ProdusentAPI.Context,
        id: UUID,
        nesteSteg: NesteStegSakInput,
    ): NesteStegSakResultat {
        val sak = produsentRepository.hentSak(id)
            ?: return Error.SakFinnesIkke("sak med id=$id finnes ikke")
        return nesteStegSak(context = context, sak = sak, nesteSteg = nesteSteg)
    }

    private suspend fun nesteStegSakByGrupperingsid(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String,
        nesteSteg: NesteStegSakInput
    ): NesteStegSakResultat {
        val sak = produsentRepository.hentSak(merkelapp = merkelapp, grupperingsid = grupperingsid)
            ?: return Error.SakFinnesIkke("sak med merkelapp='$merkelapp' og grupperingsid='$grupperingsid' finnes ikke")
        return nesteStegSak(context = context, sak = sak, nesteSteg = nesteSteg)
    }

    private suspend fun nesteStegSak(
        context: ProdusentAPI.Context,
        sak: ProdusentModel.Sak,
        nesteSteg: NesteStegSakInput,
    ): NesteStegSakResultat {
        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(
            produsent,
            sak.merkelapp,
        ) { error -> return error }

        nesteSteg.idempotencyKey?.let {
            if (produsentRepository.nesteStegSakFinnes(sak.id, it)) {
                return NesteStegSakVellykket(sak.id)
            }
        }

        val hendelse = nesteSteg.nesteStegSakHendelse(
            context.appName,
            produsent.id,
            sak,
            nesteSteg.idempotencyKey
        )

        hendelseDispatcher.send(hendelse)
        return NesteStegSakVellykket(sak.id)
    }

}