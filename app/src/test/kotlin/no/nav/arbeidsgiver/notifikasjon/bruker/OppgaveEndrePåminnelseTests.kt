package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class OppgaveEndrePåminnelseTests : DescribeSpec({

    describe("Oppgave har ingen påminnelse") {
        val (brukerRepository, engine) = setupRepoOgEngine()
        val oppgaveOpprettetTidspunkt = OffsetDateTime.now()
        val oppgave = brukerRepository.oppgaveOpprettet(opprettetTidspunkt = oppgaveOpprettetTidspunkt)

        it("Oppgave får en påminnelse opprettet") {
            val response1 = engine.queryNotifikasjonerJson()
            val harIkkePåminnelse =
                response1.getTypedContent<OffsetDateTime?>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            harIkkePåminnelse shouldBe null
            val påminnelsesTidspunkt = oppgaveOpprettetTidspunkt.plusDays(2).toLocalDateTime()

            brukerRepository.oppgavePåminnelseEndret(
                oppgave.notifikasjonId,
                HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        påminnelsesTidspunkt,
                        påminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = emptyList()
                )
            )
            val response2 = engine.queryNotifikasjonerJson()
            val påminnelse =
                response2.getTypedContent<OffsetDateTime?>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            påminnelse shouldNotBe null
        }
    }

    describe("Oppgave har påminnelse") {
        val (brukerRepository, engine) = setupRepoOgEngine()
        val oppgaveOpprettetTidspunkt = OffsetDateTime.now()
        val oppgave = brukerRepository.oppgaveOpprettet(opprettetTidspunkt = oppgaveOpprettetTidspunkt)
        brukerRepository.påminnelseOpprettet(oppgave, oppgaveOpprettetTidspunkt.plusDays(2).toLocalDateTime())

        it("Oppgave får påminnelse endret") {
            val response1 = engine.queryNotifikasjonerJson()
            val opprinneligPåminnelse =
                response1.getTypedContent<OffsetDateTime?>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            opprinneligPåminnelse shouldNotBe null

            val nyPåminnelse = opprinneligPåminnelse!!.plusDays(2)
            brukerRepository.oppgavePåminnelseEndret(
                oppgave.notifikasjonId,
                HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        nyPåminnelse.toLocalDateTime(),
                        nyPåminnelse.toLocalDateTime().inOsloAsInstant()
                    ),
                    eksterneVarsler = emptyList()
                )
            )

            val response2 = engine.queryNotifikasjonerJson()
            val oppdatertPåminnelse =
                response2.getTypedContent<OffsetDateTime?>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            oppdatertPåminnelse shouldNotBe null
            oppdatertPåminnelse!! shouldNotBeEqual opprinneligPåminnelse
        }

        it("Oppgave får påminnelse fjernet") {
            val response1 = engine.queryNotifikasjonerJson()
            val påminnelse =
                response1.getTypedContent<OffsetDateTime?>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            påminnelse shouldNotBe null

            brukerRepository.oppgavePåminnelseEndret(
                oppgave.notifikasjonId,
                null
            )

            val response2 = engine.queryNotifikasjonerJson()
            val harIkkePåminnelse =
                response2.getTypedContent<OffsetDateTime?>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            harIkkePåminnelse shouldBe null
        }
    }
})

private suspend fun BrukerRepository.oppgavePåminnelseEndret(oppgaveId: UUID, paaminnelse: HendelseModel.Påminnelse?) {
    HendelseModel.OppgavePaaminnelseEndret(
        virksomhetsnummer = "42",
        hendelseId = UUID.randomUUID(),
        påminnelse = paaminnelse,
        notifikasjonId = oppgaveId,
        kildeAppNavn = "1",
        produsentId = "1",
        frist = null
    ).also {
        oppdaterModellEtterHendelse(it)
    }
}

private fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)
    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
        }
    )
    return Pair(brukerRepository, engine)
}

