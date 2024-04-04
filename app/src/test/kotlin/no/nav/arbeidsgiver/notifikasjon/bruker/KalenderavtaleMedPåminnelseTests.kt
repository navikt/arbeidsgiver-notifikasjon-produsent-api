package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*

class KalenderavtaleMedPåminnelseTests : DescribeSpec({
    describe("kalenderavtale med påminnelse blir bumpet og klikk state clearet") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val tidspunkt = OffsetDateTime.parse("2020-12-03T10:15:30+01:00")

        val grupperingsid = "42"
        val merkelapp = "tag"
        val sak = brukerRepository.sakOpprettet(
            sakId = uuid("1"),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
        )
        val kalenderavtale0 = brukerRepository.kalenderavtaleOpprettet(
            sakId = sak.sakId,
            virksomhetsnummer = sak.virksomhetsnummer,
            merkelapp = sak.merkelapp,
            grupperingsid = sak.grupperingsid,

            opprettetTidspunkt = tidspunkt,
        )
        val kalenderavtale1 = brukerRepository.kalenderavtaleOpprettet(
            sakId = sak.sakId,
            virksomhetsnummer = sak.virksomhetsnummer,
            merkelapp = sak.merkelapp,
            grupperingsid = sak.grupperingsid,

            opprettetTidspunkt = tidspunkt.plusMinutes(10),
        )
        val kalenderavtale2 = brukerRepository.kalenderavtaleOpprettet(
            sakId = sak.sakId,
            virksomhetsnummer = sak.virksomhetsnummer,
            merkelapp = sak.merkelapp,
            grupperingsid = sak.grupperingsid,

            opprettetTidspunkt = tidspunkt.plusMinutes(20),
        )
        brukerRepository.brukerKlikket(kalenderavtale0)

        val response1 = engine.queryNotifikasjonerJson()
        it("listen er sortert og entry id=1 er klikket på") {
            val oppgaver = response1.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                kalenderavtale2.aggregateId,
                kalenderavtale1.aggregateId,
                kalenderavtale0.aggregateId
            )
            val harPåminnelse =
                response1.getTypedContent<List<OffsetDateTime?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                    .map { it != null }
            harPåminnelse shouldBe listOf(false, false, false)

            val klikketPaa =
                response1.getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
            klikketPaa shouldBe listOf(
                false,
                false,
                true,
            )
        }

        brukerRepository.påminnelseOpprettet(
            kalenderavtale0,
            tidspunkt.plusMinutes(15).inOsloLocalDateTime()
        )

        val response2 = engine.queryNotifikasjonerJson()
        it("listen er sortert på rekkefølge og entry 1 er klikket på") {
            val oppgaver = response2.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                kalenderavtale2.aggregateId,
                kalenderavtale0.aggregateId,
                kalenderavtale1.aggregateId,
            )
            val klikketPaa =
                response2.getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
            klikketPaa shouldBe listOf(
                false,
                false,
                false,
            )
            val harPåminnelse =
                response2.getTypedContent<List<Any?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                    .map { it != null }
            harPåminnelse shouldBe listOf(
                false,
                true,
                false
            )
        }
    }
})
