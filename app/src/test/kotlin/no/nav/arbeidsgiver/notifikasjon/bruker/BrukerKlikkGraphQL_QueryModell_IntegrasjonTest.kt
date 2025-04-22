package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrukerKlikkGraphQL_QueryModell_IntegrasjonTest {

    val fnr = "00000000000"
    val ansattFnr = "12344321"
    val virksomhetsnummer = "1234"
    val mottaker = NærmesteLederMottaker(fnr, ansattFnr, virksomhetsnummer)


    @Test
    fun `Brukerklikk-oppførsel`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
        ) {

            val beskjedOpprettet = brukerRepository.beskjedOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                mottakere = listOf(mottaker),
            )

            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = beskjedOpprettet.notifikasjonId,
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null
                )
            )

            /* sjekk at beskjed ikke er klikket på */
            val response = client.queryNotifikasjonerJson()

            val klikkMarkørFørKlikk =
                response.getTypedContent<Boolean>("$.notifikasjoner.notifikasjoner[0].brukerKlikk.klikketPaa")

            assertFalse(klikkMarkørFørKlikk)


            brukerRepository.brukerKlikket(beskjedOpprettet, fnr = fnr)

            /* sjekk at beskjed ikke er klikket på */
            val responseEtterKlikk = client.queryNotifikasjonerJson()
            val klikkMarkørEtterKlikk =
                responseEtterKlikk.getTypedContent<Boolean>("$.notifikasjoner.notifikasjoner[0].brukerKlikk.klikketPaa")

            assertTrue(klikkMarkørEtterKlikk)
        }
    }
}
