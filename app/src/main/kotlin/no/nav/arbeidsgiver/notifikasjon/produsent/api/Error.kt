package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
sealed class Error {
    abstract val feilmelding: String

    sealed interface TilgangsstyringError :
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat,
        MutationNySak.NySakResultat,
        MutationNyStatusSak.NyStatusSakResultat

    @JsonTypeName("UgyldigMerkelapp")
    data class UgyldigMerkelapp(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        QueryMineNotifikasjoner.MineNotifikasjonerResultat,
        MutationSoftDelete.SoftDeleteNotifikasjonResultat,
        MutationHardDelete.HardDeleteNotifikasjonResultat

    @JsonTypeName("UkjentProdusent")
    data class UkjentProdusent(
        override val feilmelding: String
    ) : Error(),
        TilgangsstyringError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        QueryMineNotifikasjoner.MineNotifikasjonerResultat,
        MutationSoftDelete.SoftDeleteNotifikasjonResultat,
        MutationHardDelete.HardDeleteNotifikasjonResultat,
        MutationNySak.NySakResultat,
        MutationNyStatusSak.NyStatusSakResultat

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
        MutationNyStatusSak.NyStatusSakResultat

    @JsonTypeName("DuplikatEksternIdOgMerkelapp")
    data class DuplikatEksternIdOgMerkelapp(
        override val feilmelding: String
    ) : Error(),
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat

    @JsonTypeName("DuplikatGrupperingsid")
    data class DuplikatGrupperingsid(
        override val feilmelding: String
    ) : Error(),
        MutationNySak.NySakResultat

    @JsonTypeName("NotifikasjonFinnesIkke")
    data class NotifikasjonFinnesIkke(
        override val feilmelding: String
    ) :
        Error(),
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationSoftDelete.SoftDeleteNotifikasjonResultat,
        MutationHardDelete.HardDeleteNotifikasjonResultat

    @JsonTypeName("UkjentRolle")
    data class UkjentRolle(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError,
        MutationNySak.NySakResultat

    @JsonTypeName("SakFinnesIkke")
    data class SakFinnesIkke(
        override val feilmelding: String,
    ):  Error(),
        MutationNyStatusSak.NyStatusSakResultat
}

