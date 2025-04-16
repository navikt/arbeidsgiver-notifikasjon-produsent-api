package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.MOTTATT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuerySakerFristTest {
    private val inloggetFnr = "0".repeat(11)
    private val altinnMottaker = AltinnMottaker(
        virksomhetsnummer = "42",
        serviceCode = "5441",
        serviceEdition = "1"
    )
    private val naermestelederMottaker = HendelseModel.NærmesteLederMottaker(
        naermesteLederFnr = inloggetFnr,
        ansattFnr = "4312",
        virksomhetsnummer = "42",
    )
    val altinnTilgangerService = AltinnTilgangerServiceStub(
        inloggetFnr to AltinnTilganger(
            harFeil = false,
            tilganger = listOf(
                AltinnTilgang(
                    altinnMottaker.virksomhetsnummer,
                    "${altinnMottaker.serviceCode}:${altinnMottaker.serviceEdition}"
                )
            ),
        )
    )

    private suspend fun newBrukerRepository(database: Database) = BrukerRepositoryImpl(database).apply {
        oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = UUID.randomUUID(),
                fnr = naermestelederMottaker.ansattFnr,
                narmesteLederFnr = naermestelederMottaker.naermesteLederFnr,
                orgnummer = naermestelederMottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )
    }


    @Test
    fun `sak uten oppgaver`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = newBrukerRepository(database)
        ktorBrukerTestServer(
            altinnTilgangerService = altinnTilgangerService,
            brukerRepository = brukerRepository,
        ) {
            brukerRepository.opprettSak(tilstander = emptyList(), mottakerSak = listOf(altinnMottaker))

            val frister = client.hentSaker().getTypedContent<List<String?>>("$.saker.saker[0].frister")
            assertTrue(frister.isEmpty())
        }
    }

    @Test
    fun `sak med oppgaver NY=medfrist, NY=utenfrist`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = newBrukerRepository(database)
        ktorBrukerTestServer(
            altinnTilgangerService = altinnTilgangerService,
            brukerRepository = brukerRepository,
        ) {
            val mottaker = listOf(altinnMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                ),
                mottakerSak = mottaker
            )

            val frister = client.hentSaker().getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
            assertEquals(listOf(frist, null), frister)
        }
    }

    @Test
    fun `sak med oppgaver nl NY=medfrist, NY=utenfrist`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = newBrukerRepository(database)
        ktorBrukerTestServer(
            altinnTilgangerService = altinnTilgangerService,
            brukerRepository = brukerRepository,
        ) {
            val mottaker = listOf(naermestelederMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to mottaker,
                    BrukerModel.Oppgave.Tilstand.NY to null to mottaker,
                ),
                mottakerSak = mottaker
            )

            val frister = client.hentSaker().getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
            assertEquals(listOf(frist, null), frister)
        }
    }

    @Test
    fun `sak med oppgaver UTFOERT=medfrist, UTGAATT=medfrist`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = newBrukerRepository(database)
        ktorBrukerTestServer(
            altinnTilgangerService = altinnTilgangerService,
            brukerRepository = brukerRepository,
        ) {
            val mottaker = listOf(naermestelederMottaker)
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.UTFOERT to frist to mottaker,
                    BrukerModel.Oppgave.Tilstand.UTGAATT to frist to mottaker,
                ),
                mottakerSak = mottaker
            )

            val frister = client.hentSaker().getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
            assertTrue(frister.isEmpty())
        }
    }

    @Test
    fun `sak med oppgaver NY=medfrist`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = newBrukerRepository(database)
        ktorBrukerTestServer(
            altinnTilgangerService = altinnTilgangerService,
            brukerRepository = brukerRepository,
        ) {
            val frist = LocalDate.now()
            brukerRepository.opprettSak(
                tilstander = listOf(
                    BrukerModel.Oppgave.Tilstand.NY to frist to listOf(naermestelederMottaker),
                ),
                mottakerSak = listOf(altinnMottaker)
            )

            val frister = client.hentSaker().getTypedContent<List<LocalDate?>>("$.saker.saker[0].frister")
            assertEquals(listOf(frist), frister)
        }
    }
}


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
        tilleggsinformasjon = null
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

private suspend fun HttpClient.hentSaker() =
    querySakerJson(
        virksomhetsnumre = listOf("42"),
        limit = 10
    )

private infix fun <A, B, C> Pair<A, B>.to(third: C) = Triple(this.first, this.second, third)