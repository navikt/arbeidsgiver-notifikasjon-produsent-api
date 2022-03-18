package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.erDriftsforstyrrelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.MottakerRegister

interface TilgangerService {
    suspend fun hentTilganger(
        context: BrukerAPI.Context,
    ): Tilganger
}

data class Tilganger(
    val tjenestetilganger: List<BrukerModel.Tilgang.Altinn> = listOf(),
    val reportee: List<BrukerModel.Tilgang.AltinnReportee> = listOf(),
    val rolle: List<BrukerModel.Tilgang.AltinnRolle> = listOf(),
    val harFeil: Boolean = false,
) {

    operator fun plus(other: Tilganger) = Tilganger(
        tjenestetilganger = this.tjenestetilganger.plus(other.tjenestetilganger),
        reportee = this.reportee.plus(other.reportee),
        rolle = this.rolle.plus(other.rolle),
        harFeil = this.harFeil || other.harFeil,
    )

    companion object {
        val EMPTY = Tilganger()
        val FAILURE = Tilganger(harFeil = true)

        fun List<Tilganger>.flatten() = this.fold(EMPTY){ x, y -> x + y }
    }
}


class TilgangerServiceImpl(
    private val altinn: Altinn,
    private val altinnRolleService: AltinnRolleService,
): TilgangerService {
    private val log = logger()

    override suspend fun hentTilganger(
        context: BrukerAPI.Context,
    ): Tilganger{
        return try {
            altinn.hentTilganger(
                context.fnr,
                context.token,
                MottakerRegister.servicecodeDefinisjoner,
                altinnRolleService.hentRoller(MottakerRegister.rolleDefinisjoner),
            )
        } catch (e: AltinnrettigheterProxyKlientFallbackException) {
            if (e.erDriftsforstyrrelse())
                log.info("Henting av Altinn-tilganger feilet", e)
            else
                log.error("Henting av Altinn-tilganger feilet", e)
            Tilganger.FAILURE
        } catch (e: Exception) {
            log.error("Henting av Altinn-tilganger feilet", e)
            Tilganger.FAILURE
        }

    }
}