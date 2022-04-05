package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Merkelapp
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.util.*

internal suspend inline fun hentSak(
    produsentRepository: ProdusentRepository,
    id: UUID,
    onError: (Error.SakFinnesIkke) -> Nothing
): ProdusentModel.Sak {
    return produsentRepository.hentSak(id)
        ?: onError(
            Error.SakFinnesIkke("Sak med id $id finnes ikke")
        )
}


internal suspend inline fun hentSak(
    produsentRepository: ProdusentRepository,
    eksternId: String,
    merkelapp: String,
    onError: (Error.SakFinnesIkke) -> Nothing
): ProdusentModel.Sak {
    return produsentRepository.hentSak(eksternId, merkelapp)
        ?: onError(
            Error.SakFinnesIkke("Sak med grupperingsid $eksternId og merkelapp $merkelapp finnes ikke")
        )
}

internal suspend inline fun hentNotifikasjon(
    produsentRepository: ProdusentRepository,
    id: UUID,
    onError: (Error.NotifikasjonFinnesIkke) -> Nothing
): ProdusentModel.Notifikasjon {
    return produsentRepository.hentNotifikasjon(id)
        ?: onError(
            Error.NotifikasjonFinnesIkke("Notifikasjon med id $id finnes ikke")
        )
}


internal suspend inline fun hentNotifikasjon(
    produsentRepository: ProdusentRepository,
    eksternId: String,
    merkelapp: String,
    onError: (Error.NotifikasjonFinnesIkke) -> Nothing
): ProdusentModel.Notifikasjon {
    return produsentRepository.hentNotifikasjon(eksternId, merkelapp)
        ?: onError(
            Error.NotifikasjonFinnesIkke("Notifikasjon med eksternId $eksternId og merkelapp $merkelapp finnes ikke")
        )
}

internal inline fun tilgangsstyrMottaker(
    produsent: Produsent,
    mottaker: Mottaker,
    onError: (error: Error.UgyldigMottaker) -> Nothing
) {
    if (!produsent.kanSendeTil(mottaker)) {
        onError(
            Error.UgyldigMottaker(
                """
                    | Ugyldig mottaker '${mottaker}'. 
                    | Gyldige mottakere er: ${produsent.tillatteMottakere}
                    """.trimMargin()
            )
        )
    }
}

internal inline fun hentProdusent(
    context: ProdusentAPI.Context,
    onMissing: (error: Error.UkjentProdusent) -> Nothing
): Produsent {
    if (context.produsent == null) {
        onMissing(
            Error.UkjentProdusent(
                "Finner ikke produsent med id ${context.appName}"
            )
        )
    } else {
        return context.produsent
    }
}

internal inline fun tilgangsstyrNyNotifikasjon(
    produsent: Produsent,
    mottakere: List<Mottaker>,
    merkelapp: String,
    onError: (Error.TilgangsstyringError) -> Nothing
) {
    for (mottaker in mottakere) {
        tilgangsstyrMottaker(produsent, mottaker) { error -> onError(error) }
    }
    tilgangsstyrMerkelapp(produsent, merkelapp) { error -> onError(error) }
}

internal inline fun tilgangsstyrMerkelapp(
    produsent: Produsent,
    merkelapp: Merkelapp,
    onError: (error: Error.UgyldigMerkelapp) -> Nothing
) {
    if (!produsent.kanSendeTil(merkelapp)) {
        onError(
            Error.UgyldigMerkelapp(
                """
                    | Ugyldig merkelapp '${merkelapp}'.
                    | Gyldige merkelapper er: ${produsent.tillatteMerkelapper}
                    """.trimMargin()
            )
        )
    }
}

internal inline fun tilgangsstyrProdusent(
    context: ProdusentAPI.Context,
    merkelapp: String,
    onError: (error: Error.TilgangsstyringError) -> Nothing
): Produsent  {
    val produsent = hentProdusent(context) { error -> onError(error) }
    tilgangsstyrMerkelapp(produsent, merkelapp) { error -> onError(error) }
    return produsent
}
