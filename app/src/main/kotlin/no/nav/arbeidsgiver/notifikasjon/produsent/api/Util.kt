package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Merkelapp
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.util.*

suspend inline fun hentNotifikasjon(
    produsentRepository: ProdusentRepository,
    id: UUID,
    onError: (Error.NotifikasjonFinnesIkke) -> Nothing
): ProdusentModel.Notifikasjon {
    return produsentRepository.hentNotifikasjon(id)
        ?: onError(
            Error.NotifikasjonFinnesIkke("Notifikasjon med id $id finnes ikke")
        )
}


suspend inline fun hentNotifikasjon(
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

inline fun tilgangsstyrMottaker(
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

inline fun hentProdusent(
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

inline fun tilgangsstyrNyNotifikasjon(
    produsent: Produsent,
    mottakere: List<Mottaker>,
    merkelapp: String,
    onError: (Error.NyNotifikasjonError) -> Nothing
) {
    for (mottaker in mottakere) {
        tilgangsstyrMottaker(produsent, mottaker) { error -> onError(error) }
    }
    tilgangsstyrMerkelapp(produsent, merkelapp) { error -> onError(error) }
}

inline fun tilgangsstyrMerkelapp(
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

