package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.Validators
import java.time.LocalDateTime

data class FutureTemporalInput(
    val den: LocalDateTime?,
    val om: ISO8601Period?,
) {
    init {
        Validators.ExactlyOneFieldGiven("FutureTemporalInput")(mapOf(
            "den" to den,
            "om" to om,
        ))
    }
    fun tilHendelseModel(): HendelseModel.LocalDateTimeOrDuration {
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
    fun tilHendelseModel(): HendelseModel.HardDeleteUpdate =
        HendelseModel.HardDeleteUpdate(
            nyTid = nyTid.tilHendelseModel(),
            strategi = strategi.hendelseType
        )
}

enum class NyTidStrategi(val hendelseType: HendelseModel.NyTidStrategi) {
    FORLENG(HendelseModel.NyTidStrategi.FORLENG),
    OVERSKRIV(HendelseModel.NyTidStrategi.OVERSKRIV);
}