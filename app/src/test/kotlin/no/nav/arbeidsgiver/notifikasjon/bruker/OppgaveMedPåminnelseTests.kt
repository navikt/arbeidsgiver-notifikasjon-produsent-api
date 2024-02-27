package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

class OppgaveMedPåminnelseTests : DescribeSpec({

    describe("oppgave med påminnelse blir bumpet og klikk state clearet") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val tidspunkt = OffsetDateTime.parse("2020-12-03T10:15:30+01:00")
        val oppgave0 = brukerRepository.oppgaveOpprettet(
            opprettetTidspunkt = tidspunkt,
        )
        val oppgave1 = brukerRepository.oppgaveOpprettet(
            opprettetTidspunkt = tidspunkt.plusMinutes(10),
        )
        val oppgave2 = brukerRepository.oppgaveOpprettet(
            opprettetTidspunkt = tidspunkt.plusMinutes(20),
        )
        brukerRepository.brukerKlikket(oppgave0)

        val response1 = engine.queryNotifikasjonerJson()
        it("listen er sortert og entry id=1 er klikket på") {
            val oppgaver = response1.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                oppgave2.aggregateId,
                oppgave1.aggregateId,
                oppgave0.aggregateId
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
            oppgave0,
            tidspunkt.plusMinutes(15).inOsloLocalDateTime()
        )

        val response2 = engine.queryNotifikasjonerJson()
        it("listen er sortert på rekkefølge og entry 1 er klikket på") {
            val oppgaver = response2.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                oppgave2.aggregateId,
                oppgave0.aggregateId,
                oppgave1.aggregateId,
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
