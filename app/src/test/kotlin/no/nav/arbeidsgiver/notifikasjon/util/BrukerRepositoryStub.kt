package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepository
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import java.util.*

open class BrukerRepositoryStub : BrukerRepository {
    override suspend fun hentNotifikasjoner(
        fnr: String,
        tilganger: BrukerModel.Tilganger
    ): List<BrukerModel.Notifikasjon> = TODO("Not yet implemented")
    override suspend fun hentSaker(
        fnr: String,
        virksomhetsnummer: List<String>,
        tilganger: BrukerModel.Tilganger,
        tekstsoek: String?,
        sakstyper: List<String>?,
        offset: Int,
        limit: Int,
        sortering: BrukerAPI.SakSortering,
        oppgaveTilstand: List<BrukerModel.Oppgave.Tilstand>?
    ): BrukerRepository.HentSakerResultat = TODO("Not yet implemented")
    override suspend fun hentSakstyper(fnr: String, tilganger: BrukerModel.Tilganger): List<String> = TODO("Not yet implemented")
    override suspend fun hentSakerForNotifikasjoner(grupperinger: List<BrukerModel.Gruppering>): Map<String, String> = TODO("Not yet implemented")
    override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String? = TODO("Not yet implemented")
    override suspend fun berikSaker(saker: List<BrukerModel.Sak>): Map<UUID, BrukerModel.Sakberikelse> = TODO("Not yet implemented")
    override suspend fun oppdaterModellEtterHendelse(
        hendelse: HendelseModel.Hendelse,
        metadata: HendelseModel.HendelseMetadata
    ) : Unit = TODO("Not yet implemented")
    override suspend fun oppdaterModellEtterNærmesteLederLeesah(nærmesteLederLeesah: NarmesteLederLeesah) : Unit = TODO("Not yet implemented")
}