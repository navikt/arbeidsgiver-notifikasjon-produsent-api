package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
internal sealed class Error {
    abstract val feilmelding: String

    sealed interface TilgangsstyringError :
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat,
        MutationNySak.NySakResultat,
        MutationDelete.HardDeleteNotifikasjonResultat,
        MutationDelete.SoftDeleteNotifikasjonResultat,
        MutationDelete.HardDeleteSakResultat,
        MutationDelete.SoftDeleteSakResultat,
        MutationKalenderavtale.NyKalenderavtaleResultat

    @JsonTypeName("UgyldigMerkelapp")
    data class UgyldigMerkelapp(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        QueryNotifikasjoner.MineNotifikasjonerResultat,
        QueryNotifikasjoner.HentNotifikasjonResultat,
        QuerySak.HentSakResultat,
        MutationDelete.SoftDeleteSakResultat,
        MutationDelete.HardDeleteSakResultat,
        MutationDelete.SoftDeleteNotifikasjonResultat,
        MutationDelete.HardDeleteNotifikasjonResultat,
        MutationNyStatusSak.NyStatusSakResultat,
        MutationNesteStegSak.NesteStegSakResultat,
        MutationKalenderavtale.OppdaterKalenderavtaleResultat,
        MutationTilleggsinformasjonSak.TilleggsinformasjonSakResultat,
        MutationOppgavePåminnelse.OppgaveEndrePaaminnelseResultat


    @JsonTypeName("UkjentProdusent")
    data class UkjentProdusent(
        override val feilmelding: String
    ) : Error(),
        TilgangsstyringError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        QueryNotifikasjoner.MineNotifikasjonerResultat,
        QueryNotifikasjoner.HentNotifikasjonResultat,
        QuerySak.HentSakResultat,
        MutationDelete.SoftDeleteSakResultat,
        MutationDelete.HardDeleteSakResultat,
        MutationDelete.SoftDeleteNotifikasjonResultat,
        MutationDelete.HardDeleteNotifikasjonResultat,
        MutationNySak.NySakResultat,
        MutationNyStatusSak.NyStatusSakResultat,
        MutationNesteStegSak.NesteStegSakResultat,
        MutationKalenderavtale.OppdaterKalenderavtaleResultat,
        MutationTilleggsinformasjonSak.TilleggsinformasjonSakResultat,
        MutationOppgavePåminnelse.OppgaveEndrePaaminnelseResultat

    @JsonTypeName("UgyldigMottaker")
    data class UgyldigMottaker(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError

    @JsonTypeName("Konflikt")
    data class Konflikt(
        override val feilmelding: String
    ) :
        Error(),
        MutationNyStatusSak.NyStatusSakResultat,
        MutationNesteStegSak.NesteStegSakResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        MutationKalenderavtale.OppdaterKalenderavtaleResultat,
        MutationTilleggsinformasjonSak.TilleggsinformasjonSakResultat

    @JsonTypeName("DuplikatEksternIdOgMerkelapp")
    data class DuplikatEksternIdOgMerkelapp(
        override val feilmelding: String,
        val idTilEksisterende: UUID
    ) : Error(),
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat,
        MutationKalenderavtale.NyKalenderavtaleResultat

    @JsonTypeName("DuplikatGrupperingsid")
    data class DuplikatGrupperingsid(
        override val feilmelding: String,
        val idTilEksisterende: UUID
    ) : Error(),
        MutationNySak.NySakResultat

    @JsonTypeName("DuplikatGrupperingsidEtterDelete")
    data class DuplikatGrupperingsidEtterDelete(
        override val feilmelding: String
    ) : Error(),
        MutationNySak.NySakResultat

    @JsonTypeName("NotifikasjonFinnesIkke")
    data class NotifikasjonFinnesIkke(
        override val feilmelding: String
    ) :
        Error(),
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        MutationDelete.SoftDeleteNotifikasjonResultat,
        MutationDelete.HardDeleteNotifikasjonResultat,
        QueryNotifikasjoner.HentNotifikasjonResultat,
        MutationKalenderavtale.OppdaterKalenderavtaleResultat,
        MutationOppgavePåminnelse.OppgaveEndrePaaminnelseResultat

    @JsonTypeName("UkjentRolle")
    data class UkjentRolle(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError,
        MutationNySak.NySakResultat

    @JsonTypeName("UgyldigPaaminnelseTidspunkt")
    data class UgyldigPåminnelseTidspunkt(
        override val feilmelding: String
    ) :
        Error(),
        MutationNyOppgave.NyOppgaveResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        MutationOppgavePåminnelse.OppgaveEndrePaaminnelseResultat

    @JsonTypeName("SakFinnesIkke")
    data class SakFinnesIkke(
        override val feilmelding: String,
    ):  Error(),
        MutationNyStatusSak.NyStatusSakResultat,
        MutationDelete.SoftDeleteSakResultat,
        MutationDelete.HardDeleteSakResultat,
        MutationKalenderavtale.NyKalenderavtaleResultat,
        QuerySak.HentSakResultat,
        MutationNesteStegSak.NesteStegSakResultat,
        MutationTilleggsinformasjonSak.TilleggsinformasjonSakResultat

    @JsonTypeName("OppgavenErAlleredeUtfoert")
    data class OppgavenErAlleredeUtfoert(
        override val feilmelding: String,
    ):  Error(),
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat,
        MutationOppgavePåminnelse.OppgaveEndrePaaminnelseResultat

    @JsonTypeName("UgyldigKalenderavtale")
    data class UgyldigKalenderavtale(
        override val feilmelding: String,
    ) : Error(),
        MutationKalenderavtale.NyKalenderavtaleResultat,
        MutationKalenderavtale.OppdaterKalenderavtaleResultat
}

