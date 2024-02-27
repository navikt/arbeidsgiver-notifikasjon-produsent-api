package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilgang
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.MOTTATT
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class QuerySakerFristTests : DescribeSpec({
    val inloggetFnr = "0".repeat(11)
    val altinnMottaker = AltinnMottaker(
        virksomhetsnummer = "42",
        serviceCode = "5441",
        serviceEdition = "1"
    )
    val naermestelederMottaker = HendelseModel.NærmesteLederMottaker(
        naermesteLederFnr = inloggetFnr,
        ansattFnr = "4312",
        virksomhetsnummer = "42",
    )

    suspend fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            altinn = AltinnStub(
                inloggetFnr to Tilganger(
                    tjenestetilganger = listOf(
                        Tilgang.Altinn(
                            altinnMottaker.virksomhetsnummer,
                            altinnMottaker.serviceCode,
                            altinnMottaker.serviceEdition
                        )
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        )
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = UUID.randomUUID(),
                fnr = naermestelederMottaker.ansattFnr,
                narmesteLederFnr = naermestelederMottaker.naermesteLederFnr,
                orgnummer = naermestelederMottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )
        return Pair(brukerRepository, engine)
    }

    describe("Query.saker med frist") {
        context("sak uten oppgaver") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            brukerRepository.opprettSak(tilstander = emptyList(), mottakerSak = listOf(altinnMottaker))

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<String?>>("$.saker.saker[0].frister")
                frister should beEmpty()
            }
        }

        context("sak med oppgaver [NY:medfrist, NY:utenfrist]") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val mottaker = listOf(altinnMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                ),
                mottakerSak = mottaker
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
                frister shouldBe listOf(frist, null)
            }
        }

        context("sak med oppgaver nl [NY:medfrist, NY:utenfrist") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val mottaker = listOf(naermestelederMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                ),
                mottakerSak = mottaker
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
                frister shouldBe listOf(frist, null)
            }
        }

        context("sak med oppgaver [UTFOERT:medfrist, UTGAATT:medfrist]") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val mottaker = listOf(naermestelederMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.UTFOERT to frist to mottaker,
                    BrukerModel.Oppgave.Tilstand.UTGAATT to frist to mottaker,
                ),
                mottakerSak = mottaker
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
                frister should beEmpty()
            }
        }

        context("sak med oppgaver [NY:medfrist]") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to listOf(naermestelederMottaker),
                ),
                mottakerSak = listOf(altinnMottaker)
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
                frister shouldBe listOf(frist)
            }
        }

        context("sak med oppgaver er sortert på frist") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val mottaker = listOf(naermestelederMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to frist.plusDays(2) to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to frist.plusDays(1) to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to frist.plusDays(3) to mottaker,
                ),
                mottakerSak = mottaker
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
                frister shouldBe listOf(frist.plusDays(1), frist.plusDays(2), frist.plusDays(3), null)
            }
        }

        context("saker er sortert på frist") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val mottaker = listOf(naermestelederMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to frist.plusDays(2) to mottaker,
                ),
                mottakerSak = mottaker
            )
            brukerRepository.opprettSak(
                tilstander = emptyList(),
                mottakerSak = mottaker
            )
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist.plusDays(3) to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                ),
                mottakerSak = mottaker
            )
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist.plusDays(1) to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                ),
                mottakerSak = mottaker
            )
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                ),
                mottakerSak = mottaker
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val saksfrister = response.getTypedContent<List<List<LocalDate?>>>("$.saker.saker[*].frister")
                saksfrister shouldBe listOf(
                    listOf(frist.plusDays(1), null),
                    listOf(frist.plusDays(2), null),
                    listOf(frist.plusDays(3), null),
                    listOf(null),
                    listOf(),
                )
            }
        }
    }
})

private suspend fun BrukerRepository.opprettSak(
    tilstander: List<Triple<BrukerModel.Oppgave.Tilstand, LocalDate?, List<HendelseModel.Mottaker>>>,
    mottakerSak: List<HendelseModel.Mottaker>,
): SakOpprettet {
    val sakId = UUID.randomUUID()
    val sakOpprettet = sakOpprettet(
        virksomhetsnummer = "42",
        produsentId = "test",
        kildeAppNavn = "test",
        sakId = sakId,
        grupperingsid = UUID.randomUUID().toString(),
        merkelapp = "tag",
        mottakere = mottakerSak,
        tittel = "yeah boi",
        lenke = "#foo",
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    )
    nyStatusSak(
        sak = sakOpprettet,
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = sakOpprettet.virksomhetsnummer,
        produsentId = sakOpprettet.produsentId,
        kildeAppNavn = sakOpprettet.kildeAppNavn,
        status = MOTTATT,
        overstyrStatustekstMed = null,
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
        hardDelete = null,
        nyLenkeTilSak = null,
    )

    for ((tilstand, frist, mottakere) in tilstander) {
        val oppgaveId = UUID.randomUUID()
        val oppgave = oppgaveOpprettet(
            notifikasjonId = oppgaveId,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = sakOpprettet.grupperingsid,
            merkelapp = sakOpprettet.merkelapp,
            eksternId = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            tekst = "tjohei",
            mottakere = mottakere,
            lenke = "#foo",
            hardDelete = null,
            frist = frist,
            påminnelse = null,
        )

        when (tilstand) {
            BrukerModel.Oppgave.Tilstand.NY -> {}
            BrukerModel.Oppgave.Tilstand.UTFOERT -> oppgaveUtført(
                oppgave,
                hardDelete = null,
                nyLenke = null,
                utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
            )

            BrukerModel.Oppgave.Tilstand.UTGAATT -> oppgaveUtgått(
                oppgave,
                hardDelete = null,
                utgaattTidspunkt = OffsetDateTime.now(),
                nyLenke = null,
            )
        }
    }
    return sakOpprettet
}

private fun TestApplicationEngine.hentSaker() =
    querySakerJson(
        virksomhetsnumre = listOf("42"),
        limit = 10,
        sortering = BrukerAPI.SakSortering.FRIST
    )

private infix fun <A, B, C> Pair<A, B>.to(third: C) = Triple(this.first, this.second, third)