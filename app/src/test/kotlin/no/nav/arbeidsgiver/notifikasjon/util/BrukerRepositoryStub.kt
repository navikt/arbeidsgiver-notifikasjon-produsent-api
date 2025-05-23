package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepository
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import java.time.OffsetDateTime
import java.util.*

open class BrukerRepositoryStub : BrukerRepository {
    override suspend fun hentNotifikasjoner(
        fnr: String,
        altinnTilganger: AltinnTilganger
    ): List<BrukerModel.Notifikasjon> = TODO("Not yet implemented")

    override suspend fun hentSaker(
        fnr: String,
        virksomhetsnummer: List<String>,
        altinnTilganger: AltinnTilganger,
        tekstsoek: String?,
        sakstyper: List<String>?,
        sortering: BrukerAPI.SakSortering,
        offset: Int,
        limit: Int,
        oppgaveTilstand: List<BrukerModel.Oppgave.Tilstand>?,
        oppgaveFilter: List<BrukerAPI.OppgaveFilterInfo.OppgaveFilterType>?
    ): BrukerRepository.HentSakerResultat = TODO("Not yet implemented")

    override suspend fun hentSakById(fnr: String, altinnTilganger: AltinnTilganger, id: UUID): BrukerModel.Sak? =
        TODO("Not yet implemented")

    override suspend fun hentSakByGrupperingsid(
        fnr: String,
        altinnTilganger: AltinnTilganger,
        grupperingsid: String,
        merkelapp: String
    ): BrukerModel.Sak? = TODO("Not yet implemented")

    override suspend fun hentSakstyper(fnr: String, altinnTilganger: AltinnTilganger): List<String> =
        TODO("Not yet implemented")

    override suspend fun hentSakerForNotifikasjoner(grupperinger: List<BrukerModel.Gruppering>): Map<String, BrukerModel.SakMetadata> =
        TODO("Not yet implemented")

    override suspend fun hentKommendeKalenderavaler(
        fnr: String,
        virksomhetsnumre: List<String>,
        altinnTilganger: AltinnTilganger
    ): List<BrukerModel.Kalenderavtale> = TODO("Not yet implemented")

    override suspend fun settNotifikasjonerSistLest(tidspunkt: OffsetDateTime, fnr: String) {
        TODO("Not yet implemented")
    }

    override suspend fun hentNotifikasjonerSistLest(fnr: String): OffsetDateTime? {
        TODO("Not yet implemented")
    }

    override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String? = TODO("Not yet implemented")
    override suspend fun berikSaker(saker: List<BrukerModel.Sak>): Map<UUID, BrukerModel.Sakberikelse> =
        TODO("Not yet implemented")

    override suspend fun oppdaterModellEtterHendelse(
        hendelse: HendelseModel.Hendelse,
        metadata: HendelseModel.HendelseMetadata
    ): Unit = TODO("Not yet implemented")

    override suspend fun oppdaterModellEtterNærmesteLederLeesah(nærmesteLederLeesah: NarmesteLederLeesah): Unit =
        TODO("Not yet implemented")
}