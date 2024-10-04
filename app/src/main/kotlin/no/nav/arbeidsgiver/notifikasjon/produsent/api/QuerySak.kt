package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.util.*

class QuerySak (
    private val produsentRepository: ProdusentRepository
    ) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<HentSakResultat>()

        runtime.wire("Query") {
            coDataFetcher("hentSak") { env ->
                hentSak(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument<UUID>("id"),
                )
            }
            coDataFetcher("hentSakMedGrupperingsid") { env ->
                hentSakMedGrupperingsid(
                    context = env.notifikasjonContext(),
                    grupperingsid = env.getTypedArgument<String>("grupperingsid"),
                    merkelapp = env.getTypedArgument<String>("merkelapp"),
                )
            }
        }
    }

    @JsonTypeName("Sak")
    data class Sak(
        val id: UUID,
        val grupperingsid: String,
        val virksomhetsnummer: String,
        val tittel: String,
        val tilleggsinformasjon: String?,
        val lenke: String?,
        val nesteSteg: String?,
        val merkelapp: String,
        val sisteStatus: SakStatus
    ) {
        companion object {
            fun fraDomene(sak: ProdusentModel.Sak): Sak {
                return Sak(
                    id = sak.id,
                    grupperingsid = sak.grupperingsid,
                    virksomhetsnummer = sak.virksomhetsnummer,
                    tittel = sak.tittel,
                    lenke = sak.lenke,
                    nesteSteg = sak.nesteSteg,
                    tilleggsinformasjon = sak.tilleggsinformasjon,
                    merkelapp = sak.merkelapp,
                    sisteStatus = when(sak.statusoppdateringer.maxBy { it.tidspunktMottatt }.status) {
                        HendelseModel.SakStatus.MOTTATT -> SakStatus.MOTTATT
                        HendelseModel.SakStatus.UNDER_BEHANDLING -> SakStatus.UNDER_BEHANDLING
                        HendelseModel.SakStatus.FERDIG -> SakStatus.FERDIG
                    }
                )
            }
        }
    }

    enum class SakStatus {
        MOTTATT,
        UNDER_BEHANDLING,
        FERDIG;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HentSakResultat


    @JsonTypeName("HentetSak")
    data class HentetSak(
        val sak: Sak,
    ) : HentSakResultat

    private suspend fun hentSak(
        context: ProdusentAPI.Context,
        id: UUID
    ) : HentSakResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sak = produsentRepository.hentSak(id)
            ?: return Error.SakFinnesIkke("Sak med id $id finnes ikke")

        tilgangsstyrMerkelapp(produsent, sak.merkelapp) { error -> return error }

        return HentetSak(Sak.fraDomene(sak))
    }

    private suspend fun hentSakMedGrupperingsid(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String
    ) : HentSakResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sak = produsentRepository.hentSak(grupperingsid, merkelapp)
            ?: return Error.SakFinnesIkke("Sak med grupperingsid $grupperingsid og merkelapp $merkelapp finnes ikke")

        tilgangsstyrMerkelapp(produsent, sak.merkelapp) { error -> return error }

        return HentetSak(Sak.fraDomene(sak))
    }
}