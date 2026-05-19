package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Merkelapp
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.util.*

object Util {
    val log = logger()
}

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
    grupperingsid: String,
    merkelapp: String,
    onError: (Error.SakFinnesIkke) -> Nothing
): ProdusentModel.Sak {
    return produsentRepository.hentSak(grupperingsid, merkelapp)
        ?: onError(
            Error.SakFinnesIkke("Sak med grupperingsid $grupperingsid og merkelapp $merkelapp finnes ikke")
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
        Util.log.warn("Ugyldig mottaker. produsent={}", produsent.id)
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
        Util.log.warn("Ukjent produsent '{}'", context.appName)
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
        Util.log.warn("Ugyldig merkelapp '{}' for produsent '{}'", merkelapp, produsent.id)
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
): Produsent {
    val produsent = hentProdusent(context) { error -> onError(error) }
    tilgangsstyrMerkelapp(produsent, merkelapp) { error -> onError(error) }
    return produsent
}

private inline fun validerMottakerMotSak(
    sak: ProdusentModel.Sak,
    virksomhetsnummer: String,
    mottakerInput: MottakerInput,
    onError: (Error.UgyldigMottaker) -> Nothing
) {
    val mottaker = mottakerInput.tilHendelseModel(virksomhetsnummer)
    if (mottaker !in sak.mottakere.berikMedFallback()) {
        onError(
            Error.UgyldigMottaker(
                """
                    | Ugyldig mottaker '${mottakerInput}'. 
                    | Mottaker må finnes på sak.
                    """.trimMargin()
            )
        )
    }
}

internal inline fun validerMottakereMotSak(
    sak: ProdusentModel.Sak?,
    virksomhetsnummer: String,
    mottakere: List<MottakerInput>,
    onError: (Error.UgyldigMottaker) -> Nothing
) {
    if (sak == null) return
    for (mottaker in mottakere) {
        validerMottakerMotSak(sak, virksomhetsnummer, mottaker, onError)
    }
}

fun String.ensurePrefix(prefix: String) =
    prefix + removePrefix(prefix)

fun String.ensureSuffix(suffix: String) =
    removeSuffix(suffix) + suffix



private val serviceCodeEditionTilRessursMap = mapOf(
    "nav_permittering-og-nedbemmaning_innsyn-i-alle-innsendte-meldinger" to ("5810" to "1"),
    "nav_sosialtjenester_digisos-avtale" to ("5867" to "1"),
    "nav_forebygge-og-redusere-sykefravar_sykefravarsstatistikk" to ("3403" to basedOnEnv(
        prod = { "2" },
        other = { "1" })),
    "nav_tiltak_arbeidstrening" to ("5332" to basedOnEnv(prod = { "2" }, other = { "1" })),
    "nav_utbetaling_endre-kontonummer-refusjon-arbeidsgiver" to ("2896" to "87"),
    "nav_tiltak_midlertidig-lonnstilskudd" to ("5516" to "1"),
    "nav_tiltak_varig-lonnstilskudd" to ("5516" to "2"),
    "nav_tiltak_sommerjobb" to ("5516" to "3"),
    "nav_tiltak_mentor" to ("5516" to "4"),
    "nav_tiltak_inkluderingstilskudd" to ("5516" to "5"),
    "nav_tiltak_varig-tilrettelagt-arbeid-ordinaer" to ("5516" to "6"),
    "nav_tiltak_adressesperre" to ("5516" to "7"),
    "nav_tiltak_tilskuddsbrev" to ("5278" to "1"),
    "nav_tiltak_ekspertbistand" to ("5384" to "1"),
    "nav_foreldrepenger_inntektsmelding" to ("4936" to "1"),
    "nav_sykepenger_inntektsmelding" to ("4936" to "1"),
    "nav_sykepenger_fritak-arbeidsgiverperiode" to ("4936" to "1"),
    "nav_sykdom-i-familien_inntektsmelding" to ("4936" to "1"),
    "nav_arbeidsforhold_aa-registeret-innsyn-arbeidsgiver" to ("5441" to "1"),
    "nav_arbeidsforhold_aa-registeret-brukerstotte" to ("5441" to "2"),
    "nav_arbeidsforhold_aa-registeret-sok-tilgang" to ("5719" to "1"),
    "nav_arbeidsforhold_aa-registeret-oppslag-samarbeidspartnere" to ("5723" to "1"),
    "nav_rekruttering_kandidater" to ("5078" to "1"),
    "nav_yrkesskade_skademelding" to ("5902" to "1"),
).entries.groupBy({ it.value }, { it.key })

/**
 * midlertidig workaround at produsenter oppretter notifikasjoner på altinn ressurs mens sak er opprettet på altinn 2 tjeneste
 * Nødvendig så lenge det finnes aktive saker på altinn 2 tjenester
 */
private fun List<Mottaker>.berikMedFallback() = flatMap {
    when (it) {
        is HendelseModel.AltinnMottaker ->
            listOf(it) + (serviceCodeEditionTilRessursMap[it.serviceCode to it.serviceEdition]?.map { ressursId ->
                HendelseModel.AltinnRessursMottaker(
                    virksomhetsnummer = it.virksomhetsnummer,
                    ressursId = ressursId,
                )
            } ?: listOf())

        else -> listOf(it)
    }
}