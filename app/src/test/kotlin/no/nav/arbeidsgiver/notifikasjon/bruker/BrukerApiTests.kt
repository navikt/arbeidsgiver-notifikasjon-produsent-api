package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics.meterRegistry
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid

@Isolate // see todo below
class BrukerApiTests : DescribeSpec({

    val fnr = "00000000000"
    val ansattFnr = "12344321"
    val virksomhetsnummer = "1234"
    val mottaker = HendelseModel.NærmesteLederMottaker(fnr, ansattFnr, virksomhetsnummer)


    describe("graphql bruker-api Query.notifikasjoner") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
        )
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
        val response = engine.queryNotifikasjonerJson()

        it("response inneholder riktig data") {
            response.getTypedContent<List<BrukerAPI.Notifikasjon>>("notifikasjoner/notifikasjoner").let {
                it shouldNot beEmpty()
                val returnedBeskjed = it[0] as BrukerAPI.Notifikasjon.Beskjed
                returnedBeskjed.merkelapp shouldBe beskjed.merkelapp
                returnedBeskjed.id shouldBe beskjed.notifikasjonId
                returnedBeskjed.brukerKlikk.klikketPaa shouldBe false

                val returnedOppgave = it[1] as BrukerAPI.Notifikasjon.Oppgave
                returnedOppgave.merkelapp shouldBe oppgave.merkelapp
                returnedOppgave.id shouldBe oppgave.notifikasjonId
                returnedOppgave.brukerKlikk.klikketPaa shouldBe false
            }
        }

        it("notifikasjoner hentet counter økt") {
            // TODO: this is flaky in parallell.
            val vellykket = meterRegistry.get("notifikasjoner_hentet").counter().count()
            vellykket - before shouldBe 2
        }
    }
})

