package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
sealed class Error {
    abstract val feilmelding: String

    sealed interface NyNotifikasjonError :
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat

    @JsonTypeName("UgyldigMerkelapp")
    data class UgyldigMerkelapp(
        override val feilmelding: String
    ) :
        Error(),
        NyNotifikasjonError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        QueryMineNotifikasjoner.MineNotifikasjonerResultat,
        MutationSoftDelete.SoftDeleteNotifikasjonResultat,
        MutationHardDelete.HardDeleteNotifikasjonResultat

    @JsonTypeName("UkjentProdusent")
    data class UkjentProdusent(
        override val feilmelding: String
    ) : Error(),
        NyNotifikasjonError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        QueryMineNotifikasjoner.MineNotifikasjonerResultat,
        MutationSoftDelete.SoftDeleteNotifikasjonResultat,
        MutationHardDelete.HardDeleteNotifikasjonResultat

    @JsonTypeName("UgyldigMottaker")
    data class UgyldigMottaker(
        override val feilmelding: String
    ) :
        Error(),
        NyNotifikasjonError

    @JsonTypeName("DuplikatEksternIdOgMerkelapp")
    data class DuplikatEksternIdOgMerkelapp(
        override val feilmelding: String
    ) : Error(),
        NyNotifikasjonError

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
        NyNotifikasjonError
}

