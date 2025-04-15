package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class OppgaveMedPåminnelseTest {


    @Test
    fun `oppgave med påminnelse blir bumpet og klikk state clearet`() =
        withTestDatabase(Bruker.databaseConfig) { database ->
            val brukerRepository = BrukerRepositoryImpl(database)
            ktorBrukerTestServer(
                brukerRepository = brukerRepository,
                altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                    AltinnTilganger(
                        harFeil = false,
                        tilganger = listOf(TEST_TILGANG_1)
                    )
                }
            ) {
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

                with(client.queryNotifikasjonerJson()) {
                    // listen er sortert og entry id=1 er klikket på
                    val oppgaver = getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
                    assertEquals(listOf(oppgave2.aggregateId, oppgave1.aggregateId, oppgave0.aggregateId), oppgaver)

                    val harPåminnelse =
                        getTypedContent<List<OffsetDateTime?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                            .map { it != null }
                    assertEquals(listOf(false, false, false), harPåminnelse)

                    val klikketPaa =
                        getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
                    assertEquals(listOf(false, false, true), klikketPaa)
                }

                brukerRepository.påminnelseOpprettet(
                    oppgave0,
                    tidspunkt.plusMinutes(15).inOsloLocalDateTime()
                )

                with(client.queryNotifikasjonerJson()) {
                    // listen er sortert på rekkefølge og entry 1 er klikket på
                    val oppgaver = getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
                    assertEquals(listOf(oppgave2.aggregateId, oppgave0.aggregateId, oppgave1.aggregateId), oppgaver)

                    val klikketPaa =
                        getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
                    assertEquals(listOf(false, false, false), klikketPaa)

                    val harPåminnelse =
                        getTypedContent<List<Any?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                            .map { it != null }
                    assertEquals(listOf(false, true, false), harPåminnelse)
                }

            }
        }
}
