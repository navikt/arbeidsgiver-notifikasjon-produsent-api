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
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.MOTTATT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class QuerySakerFristTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)


    val inloggetFnr = "0".repeat(11)
    val altinnMottakerMedTilgang = AltinnMottaker(
        virksomhetsnummer = "42",
        serviceCode = "5441",
        serviceEdition = "1"
    )
    val altinnMottakerUtenTilgang1 = altinnMottakerMedTilgang.copy(virksomhetsnummer = "1234")
    val altinnMottakerUtenTilgang2 = altinnMottakerMedTilgang.copy(serviceCode = "1234")
    val naermestelederMottakerMedTilgang = HendelseModel.NærmesteLederMottaker(
        naermesteLederFnr = inloggetFnr,
        ansattFnr = "4312",
        virksomhetsnummer = "42",
    )
    val naermestelederMottakerUtenTilgang = naermestelederMottakerMedTilgang.copy(naermesteLederFnr = "1234")

    val engine = ktorBrukerTestServer(
        altinn = AltinnStub(
            inloggetFnr to Tilganger(
                tjenestetilganger = listOf(
                    Tilgang.Altinn(
                        altinnMottakerMedTilgang.virksomhetsnummer,
                        altinnMottakerMedTilgang.serviceCode,
                        altinnMottakerMedTilgang.serviceEdition
                    )
                ),
                listOf(),
                listOf(),
            )
        ),
        brukerRepository = brukerRepository,
    )


    describe("Query.saker med frist") {
        beforeContainer {
            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = UUID.randomUUID(),
                    fnr = naermestelederMottakerMedTilgang.ansattFnr,
                    narmesteLederFnr = naermestelederMottakerMedTilgang.naermesteLederFnr,
                    orgnummer = naermestelederMottakerMedTilgang.virksomhetsnummer,
                    aktivTom = null,
                )
            )
        }

        context("sak uten oppgaver") {
            brukerRepository.opprettSak(tilstander = emptyList(), mottakerSak = listOf(altinnMottakerMedTilgang))

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<String?>>("$.saker.saker[0].frister")
                frister should beEmpty()
            }
        }

        context("sak med oppgaver [NY:medfrist:tilgang, NY:utenfrist:tilgang]") {
            val mottaker = listOf(altinnMottakerMedTilgang)
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

        context("sak med oppgaver nl [NY:medfrist:tilgang, NY:utenfrist:tilgang]") {
            val mottaker = listOf(naermestelederMottakerMedTilgang)
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

        context("sak med oppgaver [UTFOERT:medfrist:tilgang, UTGAATT:medfrist:tilgang]") {
            val mottaker = listOf(naermestelederMottakerMedTilgang)
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

        context("sak med oppgaver [NY:medfrist:ikketilgang, NY:utenfrist:ikketilgang]") {
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to listOf(altinnMottakerUtenTilgang1),
                    BrukerModel.Oppgave.Tilstand.NY to null to listOf(altinnMottakerUtenTilgang2),
                ),
                mottakerSak = listOf(naermestelederMottakerMedTilgang)
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
                frister shouldBe emptyList()
            }
        }

        context("sak med oppgaver [NY:medfrist:tilgang, NY:utenfrist:ikketilgang]") {
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to listOf(naermestelederMottakerMedTilgang),
                    BrukerModel.Oppgave.Tilstand.NY to null to listOf(naermestelederMottakerUtenTilgang),
                ),
                mottakerSak = listOf(altinnMottakerMedTilgang)
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val frister = response.getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
                frister shouldBe listOf(frist)
            }
        }

        context("sak med oppgaver er sortert på frist") {
            val mottaker = listOf(naermestelederMottakerMedTilgang)
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
            val mottaker = listOf(naermestelederMottakerMedTilgang)
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
    val sakOpprettet = SakOpprettet(
        hendelseId = sakId,
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
    ).also { oppdaterModellEtterHendelse(it) }
    NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = sakOpprettet.virksomhetsnummer,
        produsentId = sakOpprettet.produsentId,
        kildeAppNavn = sakOpprettet.kildeAppNavn,
        sakId = sakOpprettet.sakId,
        status = MOTTATT,
        overstyrStatustekstMed = null,
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
        hardDelete = null,
        nyLenkeTilSak = null,
    ).also { oppdaterModellEtterHendelse(it) }

    for ((tilstand, frist, mottakere) in tilstander) {
        val oppgaveId = UUID.randomUUID()
        HendelseModel.OppgaveOpprettet(
            hendelseId = oppgaveId,
            notifikasjonId = oppgaveId,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = sakOpprettet.grupperingsid,
            eksternId = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            merkelapp = "tag",
            tekst = "tjohei",
            mottakere = mottakere,
            lenke = "#foo",
            hardDelete = null,
            frist = frist,
            påminnelse = null,
        ).also { oppdaterModellEtterHendelse(it) }

        when (tilstand) {
            BrukerModel.Oppgave.Tilstand.NY -> null
            BrukerModel.Oppgave.Tilstand.UTFOERT -> HendelseModel.OppgaveUtført(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = oppgaveId,
                virksomhetsnummer = "1",
                produsentId = "1",
                kildeAppNavn = "1",
                hardDelete = null,
            )

            BrukerModel.Oppgave.Tilstand.UTGAATT -> HendelseModel.OppgaveUtgått(
                hendelseId = UUID.randomUUID(),
                notifikasjonId = oppgaveId,
                virksomhetsnummer = "1",
                produsentId = "1",
                kildeAppNavn = "1",
                hardDelete = null,
                utgaattTidspunkt = OffsetDateTime.now()
            )
        }?.also { oppdaterModellEtterHendelse(it) }
    }
    return sakOpprettet
}

private fun TestApplicationEngine.hentSaker() = brukerApi(
    GraphQLRequest(
        """
    query hentSaker(${'$'}virksomhetsnumre: [String!]!, ${'$'}sortering: SakSortering!, ${'$'}limit: Int){
        saker(virksomhetsnumre: ${'$'}virksomhetsnumre, sortering: ${'$'}sortering, limit: ${'$'}limit) {
            saker {
                id
                frister
                oppgaver{
                    frist
                    tilstand
                    paaminnelseTidspunkt
                }
            }
        }
    }
    """.trimIndent(),
        "hentSaker",
        mapOf(
            "virksomhetsnumre" to listOf("42"),
            "limit" to 10,
            "sortering" to BrukerAPI.SakSortering.FRIST
        )
    )
)

private infix fun <A, B, C> Pair<A, B>.to(third: C) = Triple(this.first, this.second, third)