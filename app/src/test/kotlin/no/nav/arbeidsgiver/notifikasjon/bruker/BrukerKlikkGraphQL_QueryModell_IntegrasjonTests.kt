package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class BrukerKlikkGraphQL_QueryModell_IntegrasjonTests: DescribeSpec({

    val fnr = "00000000000"
    val ansattFnr = "12344321"
    val virksomhetsnummer = "1234"
    val mottaker = NærmesteLederMottaker(fnr, ansattFnr, virksomhetsnummer)


    describe("Brukerklikk-oppførsel") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
        )

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
        val response = engine.queryNotifikasjonerJson()

        val klikkMarkørFørKlikk = response.getTypedContent<Boolean>("$.notifikasjoner.notifikasjoner[0].brukerKlikk.klikketPaa")

        it("notifikasjon er ikke klikket på") {
            klikkMarkørFørKlikk shouldBe false
        }


        brukerRepository.brukerKlikket(beskjedOpprettet, fnr = fnr)

        /* sjekk at beskjed ikke er klikket på */
        val responseEtterKlikk = engine.queryNotifikasjonerJson()
        val klikkMarkørEtterKlikk = responseEtterKlikk.getTypedContent<Boolean>("$.notifikasjoner.notifikasjoner[0].brukerKlikk.klikketPaa")

        it("notifikasjon er klikket på") {
            klikkMarkørEtterKlikk shouldBe true
        }
    }
})
