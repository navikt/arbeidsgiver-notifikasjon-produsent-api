package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.NY
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.UTFOERT
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.OppgaveTidslinjeElement
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuerySakerTidslinjeTest {
    @Test
    fun tidslinje() = withTestDatabase(Bruker.databaseConfig) { database ->
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
            val sak0 = brukerRepository.sakOpprettet()
            val sak1 = brukerRepository.sakOpprettet(lenke = null)

            // tidslinje er tom til å starte med
            with(client.fetchTidslinje(sak0)) {
                assertTrue(isEmpty())
            }
            with(client.fetchTidslinje(sak1)) {
                assertTrue(isEmpty())
            }

            // Legg til oppgave på en sak
            val oppgave0 = brukerRepository.oppgaveOpprettet(
                grupperingsid = sak0.grupperingsid,
                merkelapp = sak0.merkelapp,
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01:01+00"),
                lenke = "https://foo.bar"
            )

            // første oppgave vises på riktig sak
            with(client.fetchTidslinje(sak0)) {
                assertEquals(1, size)
                val e = first()
                e as OppgaveTidslinjeElement
                assertEquals(oppgave0.tekst, e.tekst)
                assertEquals(NY, e.tilstand)
                assertNotNull(e.id)
                assertEquals(oppgave0.lenke, e.lenke)
            }

            with(client.fetchTidslinje(sak1)) {
                assertTrue(isEmpty())
            }

            val beskjed1 = brukerRepository.beskjedOpprettet(
                grupperingsid = sak0.grupperingsid,
                merkelapp = sak0.merkelapp,
                opprettetTidspunkt = oppgave0.opprettetTidspunkt.plusHours(1),
                lenke = "https://foo.bar"
            )

            // andre beskjed på samme sak kommer i riktig rekkefølge
            with(client.fetchTidslinje(sak0)) {
                assertEquals(2, size)
                val e = first()
                e as BrukerAPI.BeskjedTidslinjeElement
                assertEquals(beskjed1.tekst, e.tekst)
                assertEquals(beskjed1.lenke, e.lenke)

                val e2 = this[1]
                e2 as OppgaveTidslinjeElement
                assertEquals(oppgave0.tekst, e2.tekst)
                assertEquals(NY, e2.tilstand)
            }

            with(client.fetchTidslinje(sak1)) {
                assertTrue(isEmpty())
            }

            val beskjed2 = brukerRepository.beskjedOpprettet(
                grupperingsid = sak1.grupperingsid,
                merkelapp = sak1.merkelapp,
                opprettetTidspunkt = oppgave0.opprettetTidspunkt.plusHours(2),
            )

            // ny beskjed på andre saken, vises kun der
            with(client.fetchTidslinje(sak0)) {
                assertEquals(2, size)
                val e = first()
                e as BrukerAPI.BeskjedTidslinjeElement
                assertEquals(beskjed1.tekst, e.tekst)

                val e2 = this[1]
                e2 as OppgaveTidslinjeElement
                assertEquals(oppgave0.tekst, e2.tekst)
                assertEquals(NY, e2.tilstand)
            }

            with(client.fetchTidslinje(sak1)) {
                assertEquals(1, size)
                val e = first()
                e as BrukerAPI.BeskjedTidslinjeElement
                assertEquals(beskjed2.tekst, e.tekst)
            }

            brukerRepository.oppgaveUtført(
                oppgave = oppgave0,
                utfoertTidspunkt = oppgave0.opprettetTidspunkt.plusHours(4),
            )

            // endret oppgave-status reflekteres i tidslinja, men posisjonen er uendret
            with(client.fetchTidslinje(sak0)) {
                assertEquals(2, size)
                val e = first()
                e as BrukerAPI.BeskjedTidslinjeElement
                assertEquals(beskjed1.tekst, e.tekst)

                val e2 = this[1]
                e2 as OppgaveTidslinjeElement
                assertEquals(oppgave0.tekst, e2.tekst)
                assertEquals(UTFOERT, e2.tilstand)
            }

            with(client.fetchTidslinje(sak1)) {
                assertEquals(1, size)
                val e = first()
                e as BrukerAPI.BeskjedTidslinjeElement
                assertEquals(beskjed2.tekst, e.tekst)
            }

            val kalenderavtale = brukerRepository.kalenderavtaleOpprettet(
                grupperingsid = sak1.grupperingsid,
                merkelapp = sak1.merkelapp,
                opprettetTidspunkt = beskjed2.opprettetTidspunkt.minusHours(1),
                startTidspunkt = beskjed2.opprettetTidspunkt.toLocalDateTime().plusHours(3),
                sluttTidspunkt = beskjed2.opprettetTidspunkt.toLocalDateTime().plusHours(4),
                sakId = sak1.sakId,
                lokasjon = HendelseModel.Lokasjon("foo", "bar", "baz"),
                lenke = "https://foo.bar"
            )

            // kalenderavtale på andre saken, vises kun der
            with(client.fetchTidslinje(sak0)) {
                assertEquals(2, size)
                val e = first()
                e as BrukerAPI.BeskjedTidslinjeElement
                assertEquals(beskjed1.tekst, e.tekst)

                val e2 = this[1]
                e2 as OppgaveTidslinjeElement
                assertEquals(oppgave0.tekst, e2.tekst)
            }

            with(client.fetchTidslinje(sak1)) {
                assertEquals(2, size)
                val e = first()
                e as BrukerAPI.KalenderavtaleTidslinjeElement
                assertEquals(kalenderavtale.tekst, e.tekst)
                assertEquals(
                    BrukerAPI.Notifikasjon.Kalenderavtale.Tilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
                    e.avtaletilstand
                )
                assertEquals(kalenderavtale.startTidspunkt.atOslo().toInstant(), e.startTidspunkt.toInstant())
                assertEquals(kalenderavtale.sluttTidspunkt?.atOslo()?.toInstant(), e.sluttTidspunkt?.toInstant())

                assertNotNull(e.lokasjon)
                assertEquals(kalenderavtale.lokasjon!!.adresse, e.lokasjon!!.adresse)
                assertEquals(kalenderavtale.lokasjon!!.poststed, e.lokasjon!!.poststed)
                assertEquals(kalenderavtale.lokasjon!!.postnummer, e.lokasjon!!.postnummer)
                assertEquals(kalenderavtale.erDigitalt, e.digitalt)
                assertEquals(kalenderavtale.lenke, e.lenke)

                val e2 = this[1]
                e2 as BrukerAPI.BeskjedTidslinjeElement
                assertEquals(beskjed2.tekst, e2.tekst)
            }
        }
    }
}

private suspend fun HttpClient.fetchTidslinje(sak: HendelseModel.SakOpprettet) = querySakerJson()
    .getTypedContent<BrukerAPI.SakerResultat>("$.saker")
    .saker
    .first { it.id == sak.sakId }
    .tidslinje
