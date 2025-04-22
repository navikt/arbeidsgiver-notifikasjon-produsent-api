package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class KalenderavtaleMedPåminnelseTest {
    @Test
    fun `kalenderavtale med påminnelse blir bumpet og klikk state clearet`() = withTestDatabase(Bruker.databaseConfig) { database ->
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

            with(client.queryNotifikasjonerJson()) {

                val oppgaver = getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
                assertEquals(
                    listOf(
                        kalenderavtale2.aggregateId,
                        kalenderavtale1.aggregateId,
                        kalenderavtale0.aggregateId
                    ),
                    oppgaver
                )
                val harPåminnelse = getTypedContent<List<OffsetDateTime?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                    .map { it != null }
                assertEquals(listOf(false, false, false), harPåminnelse)

                val klikketPaa = getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
                assertEquals(listOf(false, false, true), klikketPaa)
            }

            brukerRepository.påminnelseOpprettet(
                kalenderavtale0,
                tidspunkt.plusMinutes(15).inOsloLocalDateTime()
            )

            with(client.queryNotifikasjonerJson()) {
                val oppgaver = getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
                assertEquals(
                    listOf(
                        kalenderavtale2.aggregateId,
                        kalenderavtale0.aggregateId,
                        kalenderavtale1.aggregateId
                    ), oppgaver
                )

                val klikketPaa = getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
                assertEquals(listOf(false, false, false), klikketPaa)

                val harPåminnelse = getTypedContent<List<Any?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                        .map { it != null }
                assertEquals(listOf(false, true, false), harPåminnelse)
            }

        }
    }
}
