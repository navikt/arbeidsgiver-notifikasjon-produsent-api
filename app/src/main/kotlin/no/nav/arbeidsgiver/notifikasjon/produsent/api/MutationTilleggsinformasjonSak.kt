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

internal class MutationTilleggsinformasjonSak(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {

    private fun DataFetchingEnvironment.getTilleggsinformasjon(): TilleggsinformasjonSakInput {
        return TilleggsinformasjonSakInput(
            tilleggsinformasjon = getTypedArgumentOrNull("tilleggsinformasjon"),
            idempotencyKey = getTypedArgumentOrNull("idempotencyKey"),
        )
    }

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<TilleggsinformasjonSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("tilleggsinformasjonSak") { env ->
                tilleggsinformasjonSak(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id"),
                    tilleggsinformasjon = env.getTilleggsinformasjon()
                )
            }
            coDataFetcher("tilleggsinformasjonSakByGrupperingsid") { env ->
                tilleggsinformasjonSakByGrupperingsid(
                    context = env.notifikasjonContext(),
                    grupperingsid = env.getTypedArgument("grupperingsid"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                    tilleggsinformasjon = env.getTilleggsinformasjon()
                )
            }
        }
    }


    data class  TilleggsinformasjonSakInput(
        val tilleggsinformasjon: String?,
        val idempotencyKey: String?,
    ) {
        init {
            if(tilleggsinformasjon != null) {
                Validators.compose(
                    Validators.MaxLength("tilleggsinformasjon", 140),
                    Validators.NonIdentifying("tilleggsinformasjon"),
                )(tilleggsinformasjon)
            }
        }

        fun tilleggsinformasjonSakHendelse(
            kildeAppNavn: String,
            produsentId: String,
            tilhørendeSak: ProdusentModel.Sak,
            idempotencyKey: String?
        ): HendelseModel.TilleggsinformasjonSak {
            return HendelseModel.TilleggsinformasjonSak(
                hendelseId = UUID.randomUUID(),
                merkelapp = tilhørendeSak.merkelapp,
                grupperingsid = tilhørendeSak.grupperingsid,
                kildeAppNavn = kildeAppNavn,
                produsentId = produsentId,
                sakId = tilhørendeSak.id,
                tilleggsinformasjon = tilleggsinformasjon,
                idempotenceKey = idempotencyKey,
                virksomhetsnummer = tilhørendeSak.virksomhetsnummer,
            )
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface TilleggsinformasjonSakResultat

    @JsonTypeName("TilleggsinformasjonSakVellykket")
    data class TilleggsinformasjonSakVellykket(
        val id: UUID,
    ) : TilleggsinformasjonSakResultat


    private suspend fun tilleggsinformasjonSak(
        context: ProdusentAPI.Context,
        id: UUID,
        tilleggsinformasjon: TilleggsinformasjonSakInput,
    ): TilleggsinformasjonSakResultat {
        val sak = produsentRepository.hentSak(id)
            ?: return Error.SakFinnesIkke("sak med id=$id finnes ikke")
        return tillegsinformasjonSak(context = context, sak = sak, tilleggsinformasjon = tilleggsinformasjon)
    }

    private suspend fun tilleggsinformasjonSakByGrupperingsid(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String,
        tilleggsinformasjon: TilleggsinformasjonSakInput
    ): TilleggsinformasjonSakResultat {
        val sak = produsentRepository.hentSak(merkelapp = merkelapp, grupperingsid = grupperingsid)
            ?: return Error.SakFinnesIkke("sak med merkelapp='$merkelapp' og grupperingsid='$grupperingsid' finnes ikke")
        return tillegsinformasjonSak(context = context, sak = sak, tilleggsinformasjon = tilleggsinformasjon)
    }

    private suspend fun tillegsinformasjonSak(
        context: ProdusentAPI.Context,
        sak: ProdusentModel.Sak,
        tilleggsinformasjon: TilleggsinformasjonSakInput,
    ): TilleggsinformasjonSakResultat {
        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(
            produsent,
            sak.merkelapp,
        ) { error -> return error }

        tilleggsinformasjon.idempotencyKey?.let {
            if (produsentRepository.sakOppdateringFinnes(sak.id, it)) {
                return TilleggsinformasjonSakVellykket(sak.id)
            }
        }

        val hendelse = tilleggsinformasjon.tilleggsinformasjonSakHendelse(
            context.appName,
            produsent.id,
            sak,
            tilleggsinformasjon.idempotencyKey
        )

        hendelseDispatcher.send(hendelse)
        return TilleggsinformasjonSakVellykket(sak.id)
    }
}