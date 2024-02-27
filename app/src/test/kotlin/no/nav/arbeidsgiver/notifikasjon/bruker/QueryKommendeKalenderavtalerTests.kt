package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Kalenderavtale
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDateTime

class QueryKommendeKalenderavtalerTests: DescribeSpec({
    val now = LocalDateTime.now()

    describe("kommendeKalenderavtaler") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val grupperingsid = "42"
        val merkelapp = "tag"
        brukerRepository.sakOpprettet(
            sakId = uuid("1"),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
        )

        brukerRepository.kalenderavtaleOpprettet(
            sakId = uuid("1"),
            notifikasjonId = uuid("2"),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
            startTidspunkt = now.plusDays(1),
            sluttTidspunkt = now.plusDays(1).plusHours(1),
            tekst = "2. plass"
        )
        brukerRepository.kalenderavtaleOpprettet(
            sakId = uuid("1"),
            notifikasjonId = uuid("3"),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
            startTidspunkt = now.plusHours(1),
            sluttTidspunkt = now.plusHours(2),
            tekst = "1. plass"
        )
        brukerRepository.kalenderavtaleOpprettet(
            sakId = uuid("1"),
            notifikasjonId = uuid("4"),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
            startTidspunkt = now.minusHours(2),
            sluttTidspunkt = now.minusHours(1),
            tekst = "DNF"
        )
        brukerRepository.kalenderavtaleOpprettet(
            sakId = uuid("1"),
            notifikasjonId = uuid("5"),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
            startTidspunkt = now.minusDays(2),
            sluttTidspunkt = now.minusDays(2).plusHours(1),
            tekst = "DNF"
        )
        brukerRepository.kalenderavtaleOppdatert(
            notifikasjonId = uuid("5"),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            startTidspunkt = now.plusDays(2),
            sluttTidspunkt = now.plusDays(2).plusHours(1),
            tekst = "fra DNF til 3. plass"
        )

        val response = engine.queryKommendeKalenderavtalerJson(listOf(TEST_VIRKSOMHET_1))

        it("returnerer kommende kalenderavtaler nærmest først") {
            val kalenderavtaler = response.getTypedContent<List<Kalenderavtale>>("kommendeKalenderavtaler/avtaler")
            kalenderavtaler.size shouldBe 3
            kalenderavtaler[0].tekst shouldBe "1. plass"
            kalenderavtaler[0].startTidspunkt.toInstant() shouldBe now.plusHours(1).atOslo().toInstant()
            kalenderavtaler[1].tekst shouldBe "2. plass"
            kalenderavtaler[1].startTidspunkt.toInstant() shouldBe now.plusDays(1).atOslo().toInstant()
            kalenderavtaler[2].tekst shouldBe "fra DNF til 3. plass"
            kalenderavtaler[2].startTidspunkt.toInstant() shouldBe now.plusDays(2).atOslo().toInstant()
        }
    }
})