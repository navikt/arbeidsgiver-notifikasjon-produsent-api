package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.TestApplicationEngine
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.virksomhetsnummer
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

private val tilgang1 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "11111",
    serviceCode = "1",
    serviceEdition = "1"
)

class SakstyperQueryIngenTilgangerTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub()
    )

    describe("har ingen tilganger eller saker") {
        it("får ingen sakstyper") {
            val sakstyper = engine.querySakstyper()
            sakstyper shouldBe emptySet()
        }
    }

    describe("har ingen tilganger, men finnes saker") {
        brukerRepository.opprettSak(
            mottaker = tilgang1,
            merkelapp = "tag"
        )
        it("får ingen sakstyper") {
            val sakstyper = engine.querySakstyper()
            sakstyper shouldBe emptySet()
        }
    }
})


private fun TestApplicationEngine.querySakstyper(): Set<String> =
    brukerApi("""
        query {
            sakstyper {
                navn
            }
        }
    """.trimIndent())
        .getTypedContent<List<String>>("$.sakstyper.*.navn")
        .toSet()

private suspend fun BrukerRepository.opprettSak(
    merkelapp: String,
    mottaker: HendelseModel.Mottaker,
): HendelseModel.SakOpprettet {
    val sakId = UUID.randomUUID()
    val oppgittTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
    val sak = HendelseModel.SakOpprettet(
        hendelseId = sakId,
        sakId = sakId,
        grupperingsid = sakId.toString(),
        virksomhetsnummer = mottaker.virksomhetsnummer,
        produsentId = "test",
        kildeAppNavn = "test",
        merkelapp = merkelapp,
        mottakere = listOf(mottaker),
        tittel = "er det no sak",
        lenke = "#foo",
        oppgittTidspunkt = oppgittTidspunkt,
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    ).also {
        oppdaterModellEtterHendelse(it)
    }
    HendelseModel.NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = sak.virksomhetsnummer,
        produsentId = sak.produsentId,
        kildeAppNavn = sak.kildeAppNavn,
        sakId = sak.sakId,
        status = HendelseModel.SakStatus.MOTTATT,
        overstyrStatustekstMed = "noe",
        mottattTidspunkt = oppgittTidspunkt,
        idempotensKey = IdempotenceKey.initial(),
        oppgittTidspunkt = null,
        hardDelete = null,
        nyLenkeTilSak = null,
    ).also { hendelse ->
        oppdaterModellEtterHendelse(hendelse)
    }
    return sak
}
