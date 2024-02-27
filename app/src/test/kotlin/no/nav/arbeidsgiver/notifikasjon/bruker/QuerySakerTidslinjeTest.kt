package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.instanceOf
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.NY
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.UTFOERT
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.OppgaveTidslinjeElement
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.TidslinjeElement
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime

class QuerySakerTidslinjeTest: DescribeSpec({
    describe("tidslinje") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val sak0 = brukerRepository.sakOpprettet()
        val sak1 = brukerRepository.sakOpprettet(lenke = null)

        it("tidslinje er tom til å starte med") {
            val tidslinje0 = engine.fetchTidslinje(sak0)
            tidslinje0 should beEmpty()

            val tidslinje1 = engine.fetchTidslinje(sak1)
            tidslinje1 should beEmpty()
        }

        // Legg til oppgave på en sak
        val oppgave0 = brukerRepository.oppgaveOpprettet(
            grupperingsid = sak0.grupperingsid,
            merkelapp = sak0.merkelapp,
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01:01+00"),
        )
        it("første oppgave vises på riktig sak") {
            val tidslinje0 = engine.fetchTidslinje(sak0)
            tidslinje0 should haveSize(1)
            instanceOf<OppgaveTidslinjeElement, TidslinjeElement>(tidslinje0[0]) {
                it.tekst shouldBe oppgave0.tekst
                it.tilstand shouldBe NY
                it.id shouldNot beNull()
            }

            val tidslinje1 = engine.fetchTidslinje(sak1)
            tidslinje1 should beEmpty()
        }

        val beskjed1 = brukerRepository.beskjedOpprettet(
            grupperingsid = sak0.grupperingsid,
            merkelapp = sak0.merkelapp,
            opprettetTidspunkt = oppgave0.opprettetTidspunkt.plusHours(1),
        )
        it("andre beskjed på samme sak kommer i riktig rekkefølge") {
            val tidslinje0 = engine.fetchTidslinje(sak0)
            tidslinje0 should haveSize(2)
            instanceOf<BrukerAPI.BeskjedTidslinjeElement, TidslinjeElement>(tidslinje0[0]) {
                it.tekst shouldBe beskjed1.tekst
            }
            instanceOf<OppgaveTidslinjeElement, TidslinjeElement>(tidslinje0[1]) {
                it.tekst shouldBe oppgave0.tekst
                it.tilstand shouldBe NY
            }

            val tidslinje1 = engine.fetchTidslinje(sak1)
            tidslinje1 should beEmpty()
        }

        val beskjed2 = brukerRepository.beskjedOpprettet(
            grupperingsid = sak1.grupperingsid,
            merkelapp = sak1.merkelapp,
            opprettetTidspunkt = oppgave0.opprettetTidspunkt.plusHours(2),
        )
        it("ny beskjed på andre saken, vises kun der") {
            val tidslinje0 = engine.fetchTidslinje(sak0)
            tidslinje0 should haveSize(2)
            instanceOf<BrukerAPI.BeskjedTidslinjeElement, TidslinjeElement>(tidslinje0[0]) {
                it.tekst shouldBe beskjed1.tekst
            }
            instanceOf<OppgaveTidslinjeElement, TidslinjeElement>(tidslinje0[1]) {
                it.tekst shouldBe oppgave0.tekst
                it.tilstand shouldBe NY
            }

            val tidslinje1 = engine.fetchTidslinje(sak1)
            tidslinje1 should haveSize(1)
            instanceOf<BrukerAPI.BeskjedTidslinjeElement, TidslinjeElement>(tidslinje1[0]) {
                it.tekst shouldBe beskjed2.tekst
            }
        }


        brukerRepository.oppgaveUtført(
            oppgave = oppgave0,
            utfoertTidspunkt = oppgave0.opprettetTidspunkt.plusHours(4),
        )
        it("endret oppgave-status reflekteres i tidslinja, men posisjonen er uendret") {
            val tidslinje0 = engine.fetchTidslinje(sak0)
            tidslinje0 should haveSize(2)
            instanceOf<BrukerAPI.BeskjedTidslinjeElement, TidslinjeElement>(tidslinje0[0]) {
                it.tekst shouldBe beskjed1.tekst
            }
            instanceOf<OppgaveTidslinjeElement, TidslinjeElement>(tidslinje0[1]) {
                it.tekst shouldBe oppgave0.tekst
                it.tilstand shouldBe UTFOERT
            }

            val tidslinje1 = engine.fetchTidslinje(sak1)
            tidslinje1 should haveSize(1)
            instanceOf<BrukerAPI.BeskjedTidslinjeElement, TidslinjeElement>(tidslinje1[0]) {
                it.tekst shouldBe beskjed2.tekst
            }
        }

        val kalenderavtale = brukerRepository.kalenderavtaleOpprettet(
            grupperingsid = sak1.grupperingsid,
            merkelapp = sak1.merkelapp,
            opprettetTidspunkt = beskjed2.opprettetTidspunkt.minusHours(1),
            startTidspunkt = beskjed2.opprettetTidspunkt.toLocalDateTime().plusHours(3),
            sluttTidspunkt = beskjed2.opprettetTidspunkt.toLocalDateTime().plusHours(4),
            sakId = sak1.sakId,
        )
        it("kalenderavtale på andre saken, vises kun der") {
            val tidslinje0 = engine.fetchTidslinje(sak0)
            tidslinje0 should haveSize(2)
            instanceOf<BrukerAPI.BeskjedTidslinjeElement, TidslinjeElement>(tidslinje0[0]) {
                it.tekst shouldBe beskjed1.tekst
            }
            instanceOf<OppgaveTidslinjeElement, TidslinjeElement>(tidslinje0[1]) {
                it.tekst shouldBe oppgave0.tekst
            }

            val tidslinje1 = engine.fetchTidslinje(sak1)
            tidslinje1 should haveSize(2)
            instanceOf<BrukerAPI.KalenderavtaleTidslinjeElement, TidslinjeElement>(tidslinje1[0]) {
                it.tekst shouldBe kalenderavtale.tekst
                it.avtaletilstand shouldBe BrukerAPI.Notifikasjon.Kalenderavtale.Tilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
                it.startTidspunkt.toInstant() shouldBe kalenderavtale.startTidspunkt.atOslo().toInstant()
                it.sluttTidspunkt?.toInstant() shouldBe kalenderavtale.sluttTidspunkt?.atOslo()?.toInstant()
                it.lokasjon shouldNot beNull()
                it.lokasjon!!.adresse shouldBe kalenderavtale.lokasjon!!.adresse
                it.lokasjon!!.poststed shouldBe kalenderavtale.lokasjon!!.poststed
                it.lokasjon!!.postnummer shouldBe kalenderavtale.lokasjon!!.postnummer
                it.digitalt shouldBe kalenderavtale.erDigitalt
            }
            instanceOf<BrukerAPI.BeskjedTidslinjeElement, TidslinjeElement>(tidslinje1[1]) {
                it.tekst shouldBe beskjed2.tekst
            }
        }
    }
})

private fun TestApplicationEngine.fetchTidslinje(sak: HendelseModel.SakOpprettet)
        = querySakerJson()
    .getTypedContent<BrukerAPI.SakerResultat>("$.saker")
    .saker
    .first { it.id == sak.sakId }
    .tidslinje

suspend inline fun <reified SubClass: Class, Class: Any>TestScope.instanceOf(
    subject: Class,
    crossinline test: suspend TestScope.(it: SubClass) -> Unit
)  {
    subject shouldBe instanceOf<SubClass>()
    test(subject as SubClass)
}