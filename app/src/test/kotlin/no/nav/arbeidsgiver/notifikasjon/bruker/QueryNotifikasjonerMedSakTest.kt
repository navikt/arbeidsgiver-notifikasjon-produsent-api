package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QueryNotifikasjonerMedSakTest {

    @Test
    fun `Query#notifikasjoner med sak`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(TEST_TILGANG_1, TEST_TILGANG_2)
                )
            }
        ) {
            val opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00")

            val oppgaveUtenSakOpprettet = brukerRepository.oppgaveOpprettet(
                grupperingsid = "0",
                opprettetTidspunkt = opprettetTidspunkt,
                tekst = "oppgave uten sak",
                frist = LocalDate.parse("2007-12-03"),
            )

            val beskjedUtenSakOpprettet = brukerRepository.beskjedOpprettet(
                grupperingsid = "1",
                opprettetTidspunkt = opprettetTidspunkt.minusHours(1),
                tekst = "beskjed uten sak",
            )

            val oppgaveMedSakOpprettet = brukerRepository.oppgaveOpprettet(
                grupperingsid = "2",
                opprettetTidspunkt = opprettetTidspunkt.minusHours(2),
                tekst = "oppgave uten sak",
                frist = LocalDate.parse("2007-12-03"),
            ).also {
                brukerRepository.sakOpprettet(
                    grupperingsid = it.grupperingsid!!,
                    merkelapp = it.merkelapp,
                    mottakere = it.mottakere,
                    tittel = "Sakstittel for oppgave",
                    mottattTidspunkt = OffsetDateTime.now(),
                    tilleggsinformasjon = "Tilleggsinformasjon om saken"

                )
            }

            val beskjedMedSakOpprettet = brukerRepository.beskjedOpprettet(
                mottakere = listOf(TEST_MOTTAKER_2),
                grupperingsid = "3",
                opprettetTidspunkt = opprettetTidspunkt.minusHours(3),
                tekst = "beskjed med sak",
            ).also {
                brukerRepository.sakOpprettet(
                    grupperingsid = it.grupperingsid!!,
                    merkelapp = it.merkelapp,
                    mottakere = it.mottakere,
                    tittel = "Sakstittel for beskjed",
                    mottattTidspunkt = OffsetDateTime.now(),
                )
            }

            val kalenderavtaleMedSak = brukerRepository.sakOpprettet(
                mottakere = listOf(TEST_MOTTAKER_2),
                grupperingsid = "4",
                tittel = "Sakstittel for kalenderavtale",
                mottattTidspunkt = OffsetDateTime.now(),

                ).let { sak ->
                brukerRepository.kalenderavtaleOpprettet(
                    opprettetTidspunkt = opprettetTidspunkt.minusHours(4),
                    grupperingsid = sak.grupperingsid,
                    mottakere = sak.mottakere,
                    merkelapp = sak.merkelapp,
                    tekst = "kalenderavtale med sak",
                    sakId = sak.sakId,
                )
            }

            with(
                client.queryNotifikasjonerJson()
                    .getTypedContent<List<BrukerAPI.Notifikasjon>>("notifikasjoner/notifikasjoner")
            ) {
                (this[0] as BrukerAPI.Notifikasjon.Oppgave).let {
                    assertEquals(oppgaveUtenSakOpprettet.aggregateId, it.id)
                    assertNull(it.sak)
                }
                (this[1] as BrukerAPI.Notifikasjon.Beskjed).let {
                    assertEquals(beskjedUtenSakOpprettet.aggregateId, it.id)
                    assertNull(it.sak)
                }
                (this[2] as BrukerAPI.Notifikasjon.Oppgave).let {
                    assertEquals(oppgaveMedSakOpprettet.aggregateId, it.id)
                    assertNotNull(it.sak)
                    assertEquals("Sakstittel for oppgave", it.sak!!.tittel)
                    assertEquals("Tilleggsinformasjon om saken", it.sak!!.tilleggsinformasjon)
                }
                (this[3] as BrukerAPI.Notifikasjon.Beskjed).let {
                    assertEquals(beskjedMedSakOpprettet.aggregateId, it.id)
                    assertNotNull(it.sak)
                    assertEquals("Sakstittel for beskjed", it.sak!!.tittel)
                }
                (this[4] as BrukerAPI.Notifikasjon.Kalenderavtale).let {
                    assertEquals(kalenderavtaleMedSak.aggregateId, it.id)
                    assertEquals(kalenderavtaleMedSak.opprettetTidspunkt.toInstant(), it.sorteringTidspunkt.toInstant())
                    assertNotNull(it.sak)
                    assertEquals("Sakstittel for kalenderavtale", it.sak!!.tittel)
                }
                assertEquals(5, size)
            }
        }
    }
}

