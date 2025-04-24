package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics.meterRegistry
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BrukerApiTest {

    val fnr = "00000000000"
    val ansattFnr = "12344321"
    val virksomhetsnummer = "1234"
    val mottaker = HendelseModel.NærmesteLederMottaker(fnr, ansattFnr, virksomhetsnummer)


    @Test
    fun `graphql bruker-api Query#notifikasjoner`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
        ) {
            val beskjed = brukerRepository.beskjedOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                mottakere = listOf(mottaker),
            )
            val oppgave = brukerRepository.oppgaveOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                mottakere = listOf(mottaker),
            )

            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = uuid("123"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null
                )
            )

            val before = meterRegistry.get("notifikasjoner_hentet").counter().count()
            val response = client.queryNotifikasjonerJson()

            response.getTypedContent<List<BrukerAPI.Notifikasjon>>("notifikasjoner/notifikasjoner").let {
                assertFalse(it.isEmpty())

                val returnedBeskjed = it[0] as BrukerAPI.Notifikasjon.Beskjed
                assertEquals(beskjed.merkelapp, returnedBeskjed.merkelapp)
                assertEquals(beskjed.notifikasjonId, returnedBeskjed.id)
                assertEquals(false, returnedBeskjed.brukerKlikk.klikketPaa)

                val returnedOppgave = it[1] as BrukerAPI.Notifikasjon.Oppgave
                assertEquals(oppgave.merkelapp, returnedOppgave.merkelapp)
                assertEquals(oppgave.notifikasjonId, returnedOppgave.id)
                assertEquals(false, returnedOppgave.brukerKlikk.klikketPaa)
            }

            val vellykket = meterRegistry.get("notifikasjoner_hentet").counter().count()
            assertEquals(2.0, vellykket - before)

        }
    }
}

