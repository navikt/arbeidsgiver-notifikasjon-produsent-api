package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.SakSortering.FRIST
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class SorteringAvSakerPåFristTest : DescribeSpec({

    describe("Sak som med oppgave") {
        val database = testDatabase(Bruker.databaseConfig)
        val repo = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = repo,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        /**
         * Sakene 2, 3, 4 og så 1 blir sortert etter frist, mens 6 og 8 vil bli sortert før 7 fordi disse har oppgaver.
         * sak 8 kommer før 6 fordi den er oppdatert sist.
         */
        val sak2 = repo.opprettSakMedOppgaver( "2023-01-15", "2023-01-04")
        val sak3 = repo.opprettSakMedOppgaver( "2023-01-05", "2023-01-25")
        val sak4 = repo.opprettSakMedOppgaver( "2023-01-06", "2023-01-06")
        val sak1 = repo.opprettSakMedOppgaver( "2023-01-10", "2023-01-10")
        val sak5 = repo.opprettSakMedOppgaver( "2023-01-07", null)
        val sak6 = repo.opprettSakMedOppgaver( null)
        val sak7 = repo.opprettSakMedOppgaver()
        val sak8 = repo.opprettSakMedOppgaver( null)

        val res = engine.querySakerJson(virksomhetsnummer = TEST_VIRKSOMHET_1, limit = 10, sortering = FRIST)
            .getTypedContent<List<UUID>>("$.saker.saker.*.id")

        res shouldBe listOf(sak2, sak3, sak4, sak5, sak1, sak8, sak6, sak7)
    }
})

private suspend fun BrukerRepository.opprettSakMedOppgaver(
    vararg frister: String?,
) = sakOpprettet(
    oppgittTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
    mottattTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
).also { sak ->
    nyStatusSak(
        sak,
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
    )
    for (frist in frister) {
        oppgaveOpprettet(
            grupperingsid = sak.grupperingsid,
            merkelapp = sak.merkelapp,
            frist = frist?.let { LocalDate.parse(it) },
            opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
        )
    }
}.sakId
