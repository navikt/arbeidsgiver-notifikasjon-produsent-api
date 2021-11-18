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
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        QueryMineNotifikasjoner.MineNotifikasjonerResultat,
        MutationSoftDelete.SoftDeleteNotifikasjonResultat,
        MutationHardDelete.HardDeleteNotifikasjonResultat

    @JsonTypeName("UkjentProdusent")
    data class UkjentProdusent(
        override val feilmelding: String
    ) : Error(),
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat,
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
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat,
        NyNotifikasjonError

    @JsonTypeName("DuplikatEksternIdOgMerkelapp")
    data class DuplikatEksternIdOgMerkelapp(
        override val feilmelding: String
    ) : Error(),
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat

    @JsonTypeName("NotifikasjonFinnesIkke")
    data class NotifikasjonFinnesIkke(
        override val feilmelding: String
    ) :
        Error(),
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationSoftDelete.SoftDeleteNotifikasjonResultat,
        MutationHardDelete.HardDeleteNotifikasjonResultat
}

