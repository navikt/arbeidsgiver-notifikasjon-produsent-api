package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Merkelapp
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.virksomhetsnummer
import java.util.*

suspend inline fun ProdusentRepository.hentNotifikasjonFoo(
    id: UUID,
    onError: (Error.NotifikasjonFinnesIkke) -> Nothing
): ProdusentModel.Notifikasjon {
    return hentNotifikasjon(id)
        ?: onError(
            Error.NotifikasjonFinnesIkke("Notifikasjon med id $id finnes ikke")
        )
}


suspend inline fun ProdusentRepository.hentNotifikasjonFoo(
    eksternId: String,
    merkelapp: String,
    onError: (Error.NotifikasjonFinnesIkke) -> Nothing
): ProdusentModel.Notifikasjon {
    return hentNotifikasjon(eksternId, merkelapp)
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

val ProdusentModel.Notifikasjon.virksomhetsnummer: String
    get() = when (this) {
        is ProdusentModel.Oppgave -> this.mottaker.virksomhetsnummer
        is ProdusentModel.Beskjed -> this.mottaker.virksomhetsnummer
    }

inline fun tilgangsstyrNyNotifikasjon(
    produsent: Produsent,
    mottaker: Mottaker,
    merkelapp: String,
    onError: (NyNotifikasjonError) -> Nothing
) {
    tilgangsstyrMottaker(produsent, mottaker) { error -> onError(error) }
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

