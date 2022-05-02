package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import java.time.Duration
import java.time.LocalDateTime

data class FutureTemporalInput(
    val den: LocalDateTime?,
    val om: ISO8601Period?,
) {
    fun tilDomene(): HendelseModel.LocalDateTimeOrDuration {
        if (den != null) {
            return HendelseModel.LocalDateTimeOrDuration.LocalDateTime(den)
        }
        if (om != null) {
            return HendelseModel.LocalDateTimeOrDuration.Duration(om)
        }
        throw RuntimeException("Feil format")
    }
}
data class HardDeleteUpdateInput(
    val nyTid: FutureTemporalInput,
    val strategi: NyTidStrategi,
) {
    fun tilDomene(): HendelseModel.HardDeleteUpdate =
        HendelseModel.HardDeleteUpdate(
            nyTid = nyTid.tilDomene(),
            strategi = strategi.hendelseType
        )
}

enum class NyTidStrategi(val hendelseType: HendelseModel.NyTidStrategi) {
    FORLENG(HendelseModel.NyTidStrategi.FORLENG),
    OVERSKRIV(HendelseModel.NyTidStrategi.OVERSKRIV);
}