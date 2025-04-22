package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRessursMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.FERDIG
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.MOTTATT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuerySakerTest {
    private val fallbackTimeNotUsed = OffsetDateTime.parse("2020-01-01T01:01:01Z")

    @Test
    fun `med sak opprettet men ingen status`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "nav_test_foo-ressursid")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            val sakOpprettet = brukerRepository.sakOpprettet(
                virksomhetsnummer = "42",
                merkelapp = "tag",
                mottakere = listOf(AltinnMottaker("5441", "1", "42")),
                oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
                mottattTidspunkt = OffsetDateTime.now(),
                tilleggsinformasjon = "tilleggsinformasjon"
            )

            with(client.hentSaker().getTypedContent<BrukerAPI.Sak>("saker/saker/0")) {
                assertEquals(sakOpprettet.sakId, id)
                assertEquals("tag", merkelapp)
                assertEquals(sakOpprettet.lenke, lenke)
                assertEquals(sakOpprettet.tittel, tittel)
                assertEquals(sakOpprettet.virksomhetsnummer, virksomhet.virksomhetsnummer)
                assertEquals("Mottatt", sisteStatus.tekst)
                assertEquals(sakOpprettet.opprettetTidspunkt(fallbackTimeNotUsed), sisteStatus.tidspunkt)
                assertEquals("tilleggsinformasjon", tilleggsinformasjon)
            }
        }
    }

    @Test
    fun `med sak og status`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "nav_test_foo-ressursid")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            val sakOpprettet = brukerRepository.sakOpprettet(
                virksomhetsnummer = "43",
                grupperingsid = "42",
                merkelapp = "tag",
                lenke = null,
                mottakere = listOf(AltinnRessursMottaker("43", "nav_test_foo-ressursid")),
                oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
                mottattTidspunkt = OffsetDateTime.now(),
            )
            brukerRepository.nyStatusSak(
                sak = sakOpprettet,
                status = MOTTATT,
                overstyrStatustekstMed = "noe",
                oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
                mottattTidspunkt = OffsetDateTime.now(),
                idempotensKey = IdempotenceKey.initial(),
            )

            with(
                client.hentSaker(
                    virksomhetsnumre = listOf("43"),
                ).getTypedContent<BrukerAPI.Sak>("saker/saker/0")
            ) {
                assertEquals(sakOpprettet.sakId, id)
                assertEquals("tag", merkelapp)
                assertEquals(sakOpprettet.lenke, lenke)
                assertEquals(sakOpprettet.tittel, tittel)
                assertEquals(sakOpprettet.virksomhetsnummer, virksomhet.virksomhetsnummer)
                assertEquals("noe", sisteStatus.tekst)
            }
        }
    }

    @Test
    fun `paginering med offset og limit angitt sortert på oppdatert`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "nav_test_foo-ressursid")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            val forventetRekkefoelge = listOf(
                uuid("3"),
                uuid("1"),
                uuid("4"),
            )

            brukerRepository.opprettSakMedTidspunkt(
                forventetRekkefoelge[0],
                Duration.ofHours(1),
                Duration.ofHours(5)
            )
            brukerRepository.opprettSakMedTidspunkt(
                forventetRekkefoelge[1],
                Duration.ofHours(2),
                Duration.ofHours(4)
            )
            val sak = brukerRepository.opprettSakMedTidspunkt(forventetRekkefoelge[2], Duration.ofHours(3))

            // saksrekkefølge er korrekt innenfor page
            with(client.hentSaker(offset = 0, limit = 3)) {
                assertEquals(forventetRekkefoelge[0], getTypedContent<BrukerAPI.Sak>("saker/saker/0").id)
                assertEquals(forventetRekkefoelge[1], getTypedContent<BrukerAPI.Sak>("saker/saker/1").id)
                assertEquals(forventetRekkefoelge[2], getTypedContent<BrukerAPI.Sak>("saker/saker/2").id)
                assertEquals(3, getTypedContent<Int>("saker/totaltAntallSaker"))
            }

            // Saksrekkefølge blir korrekt med eldste først sortering
            with(client.hentSaker(offset = 0, limit = 3, sortering = BrukerAPI.SakSortering.ELDSTE)) {
                assertEquals(forventetRekkefoelge[2], getTypedContent<BrukerAPI.Sak>("saker/saker/0").id)
                assertEquals(forventetRekkefoelge[1], getTypedContent<BrukerAPI.Sak>("saker/saker/1").id)
                assertEquals(forventetRekkefoelge[0], getTypedContent<BrukerAPI.Sak>("saker/saker/2").id)
                assertEquals(3, getTypedContent<Int>("saker/totaltAntallSaker"))
            }

            // sist oppdaterte sak først
            with(client.hentSaker(offset = 0, limit = 1)) {
                assertEquals(forventetRekkefoelge[0], getTypedContent<BrukerAPI.Sak>("saker/saker/0").id)
                assertEquals(3, getTypedContent<Int>("saker/totaltAntallSaker"))
            }

            // mellomste sak ved offset 1
            with(client.hentSaker(offset = 1, limit = 1)) {
                assertEquals(forventetRekkefoelge[1], getTypedContent<BrukerAPI.Sak>("saker/saker/0").id)
                assertEquals(3, getTypedContent<Int>("saker/totaltAntallSaker"))
            }

            // eldste sak ved offset 2
            with(client.hentSaker(offset = 2, limit = 1)) {
                assertEquals(forventetRekkefoelge[2], getTypedContent<BrukerAPI.Sak>("saker/saker/0").id)
                assertEquals(3, getTypedContent<Int>("saker/totaltAntallSaker"))
            }

            // utenfor offset
            with(client.hentSaker(offset = 3, limit = 1)) {
                assertTrue(getTypedContent<List<Any>>("saker/saker").isEmpty())
                assertEquals(3, getTypedContent<Int>("saker/totaltAntallSaker"))
            }

            // offset og limit 0 gir fortsatt totalt antall saker
            with(client.hentSaker(offset = 0, limit = 0)) {
                assertTrue(getTypedContent<List<Any>>("saker/saker").isEmpty())
                assertEquals(3, getTypedContent<Int>("saker/totaltAntallSaker"))
            }

            // oppgaveOpprettet oppdaterer sorteringen
            brukerRepository.oppgaveOpprettet(sak = sak, opprettetTidspunkt = OffsetDateTime.now())
            with(client.hentSaker(offset = 0, limit = 3)) {
                assertEquals(forventetRekkefoelge[2], getTypedContent<BrukerAPI.Sak>("saker/saker/0").id)
                assertEquals(forventetRekkefoelge[0], getTypedContent<BrukerAPI.Sak>("saker/saker/1").id)
                assertEquals(forventetRekkefoelge[1], getTypedContent<BrukerAPI.Sak>("saker/saker/2").id)
            }
        }
    }

    @Test
    fun tekstsøk() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "nav_test_foo-ressursid")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            val sak1 =
                brukerRepository.opprettSakForTekstsøk(
                    "pippi langstrømpe er friskmeldt",
                    MOTTATT,
                    "herr nilson er syk"
                )
            val sak2 =
                brukerRepository.opprettSakForTekstsøk("donald duck er permittert", FERDIG, "saken er avblåst")

            // søk på tittel returnerer riktig sak
            with(client.hentSaker(tekstsoek = "pippi").getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(1, size)
                assertEquals(sak1.sakId, first().id)
            }

            // søk på status returnerer riktig sak
            with(client.hentSaker(tekstsoek = "ferdig").getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(1, size)
                assertEquals(sak2.sakId, first().id)
            }

            // søk på statustekst returnerer riktig sak
            with(client.hentSaker(tekstsoek = "avblåst").getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(1, size)
                assertEquals(sak2.sakId, first().id)
            }

            brukerRepository.nyStatusSak(
                sak = sak1,
                status = FERDIG,
                overstyrStatustekstMed = "i boks med sløyfe på",
                idempotensKey = IdempotenceKey.initial(),
            )

            // søk på opprinnelig statustekst returnerer riktig sak
            with(client.hentSaker(tekstsoek = "nilson").getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(1, size)
                assertEquals(sak1.sakId, first().id)
            }

            // søk på ny statustekst returnerer riktig sak
            with(client.hentSaker(tekstsoek = "sløyfe").getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(1, size)
                assertEquals(sak1.sakId, first().id)
            }
        }
    }

    @Test
    fun `søk på tvers av virksomheter`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "nav_test_foo-ressursid")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            val sak1 = brukerRepository.opprettSak(uuid("1"), "42", AltinnMottaker("5441", "1", "42"))
            val sak2 =
                brukerRepository.opprettSak(uuid("2"), "43", AltinnRessursMottaker("43", "nav_test_foo-ressursid"))

            // hentSaker med tom liste av virksomhetsnumre gir tom liste
            with(client.hentSaker(virksomhetsnumre = listOf()).getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(0, size)
            }

            // hentSaker med liste av virksomhetsnumre=42 gir riktig sak
            with(client.hentSaker(listOf("42")).getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(1, size)
                assertEquals(sak1.sakId, first().id)
            }

            // hentSaker med liste av virksomhetsnumre=43 gir riktig sak
            with(client.hentSaker(listOf("43")).getTypedContent<List<BrukerAPI.Sak>>("saker/saker")) {
                assertEquals(1, size)
                assertEquals(sak2.sakId, first().id)
            }

            // hentSaker med liste av virksomhetsnumre=42,43 gir riktig sak
            with(
                client.hentSaker(virksomhetsnumre = listOf("42", "43"))
                    .getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
            ) {
                assertEquals(2, size)
            }
        }
    }

    @Test
    fun `søk på type sak`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "nav_test_foo-ressursid")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            brukerRepository.opprettSak(
                uuid("1"),
                "42",
                AltinnMottaker("5441", "1", "42"),
                "merkelapp1"
            ) // tilgang til 42
            brukerRepository.opprettSak(
                uuid("2"),
                "43",
                AltinnRessursMottaker("43", "nav_test_foo-ressursid"),
                "merkelapp2"
            ) // tilgang til 43
            brukerRepository.opprettSak(
                uuid("3"),
                "44",
                AltinnMottaker("5441", "1", "44"),
                "merkelapp3"
            ) // ikke tilgang til 44
            brukerRepository.opprettSak(
                uuid("4"),
                "45",
                AltinnMottaker("5441", "1", "45"),
                "merkelapp1"
            ) // ikke tilgang til 45


            // søk på null sakstyper returnere alle
            with(
                client.hentSaker(listOf("42", "43", "44", "45")).getTypedContent<List<UUID>>("$.saker.saker.*.id")
            ) {
                assertEquals(listOf(uuid("1"), uuid("2")).sorted(), sorted())
            }

            with(
                client.hentSaker(listOf("42", "43", "44", "45"))
                    .getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
            ) {
                assertEquals(listOf("merkelapp1", "merkelapp2"), this)
            }

            // søk på merkelapp1
            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf("merkelapp1")
                ).getTypedContent<List<UUID>>("$.saker.saker.*.id")
            ) {
                assertEquals(listOf(uuid("1")), this)
            }

            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf("merkelapp1")
                ).getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
            ) {
                assertEquals(listOf("merkelapp1", "merkelapp2"), this)
            }


            // søk på merkelapp1 og merkelapp2
            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf("merkelapp1", "merkelapp2")
                ).getTypedContent<List<UUID>>("$.saker.saker.*.id")
            ) {
                assertEquals(listOf(uuid("1"), uuid("2")).sorted(), sorted())
            }

            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf("merkelapp1", "merkelapp2")
                ).getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
            ) {
                assertEquals(listOf("merkelapp1", "merkelapp2"), this)
            }

            // søk på merkelapp3
            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf("merkelapp3")
                ).getTypedContent<List<UUID>>("$.saker.saker.*.id")
            ) {
                assertTrue(isEmpty())
            }

            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf("merkelapp3")
                ).getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
            ) {
                assertEquals(listOf("merkelapp1", "merkelapp2"), this)
            }

            // søk på tom liste
            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf()
                ).getTypedContent<List<UUID>>("$.saker.saker.*.id")
            ) {
                assertTrue(isEmpty())
            }

            with(
                client.hentSaker(
                    listOf("42", "43", "44", "45"),
                    sakstyper = listOf()
                ).getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
            ) {
                assertEquals(listOf("merkelapp1", "merkelapp2"), this)
            }
        }
    }
}

private suspend fun BrukerRepository.opprettSakForTekstsøk(
    tittel: String,
    status: SakStatus = MOTTATT,
    overstyrStatustekst: String? = null,
): SakOpprettet {
    val sakOpprettet = sakOpprettet(
        virksomhetsnummer = "42",
        merkelapp = "tag",
        mottakere = listOf(AltinnMottaker("5441", "1", "42")),
        tittel = tittel,
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
    )
    nyStatusSak(
        sak = sakOpprettet,
        status = status,
        overstyrStatustekstMed = overstyrStatustekst,
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
    )
    return sakOpprettet
}

private suspend fun BrukerRepository.opprettSakMedTidspunkt(
    sakId: UUID,
    opprettetShift: Duration,
    vararg restShift: Duration,
): SakOpprettet {
    val shift = listOf(opprettetShift) + restShift
    val mottattTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
    val sak = sakOpprettet(
        sakId = sakId,
        grupperingsid = sakId.toString(),
        virksomhetsnummer = "42",
        merkelapp = "tag",
        mottakere = listOf(AltinnMottaker("5441", "1", "42")),
        mottattTidspunkt = mottattTidspunkt.plus(opprettetShift),
    )

    shift.forEach {
        nyStatusSak(
            sak = sak,
            status = MOTTATT,
            overstyrStatustekstMed = "noe",
            mottattTidspunkt = mottattTidspunkt.plus(it),
            idempotensKey = IdempotenceKey.initial(),
        )
    }
    return sak
}

private suspend fun BrukerRepository.opprettSak(
    sakId: UUID,
    virksomhetsnummer: String,
    mottaker: Mottaker,
    merkelapp: String = "tag",
): SakOpprettet {
    val oppgittTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
    val sak = sakOpprettet(
        sakId = sakId,
        virksomhetsnummer = virksomhetsnummer,
        merkelapp = merkelapp,
        mottakere = listOf(mottaker),
        oppgittTidspunkt = oppgittTidspunkt,
        mottattTidspunkt = OffsetDateTime.now(),
    )
    nyStatusSak(
        sak,
        status = MOTTATT,
        overstyrStatustekstMed = "noe",
        mottattTidspunkt = oppgittTidspunkt,
        idempotensKey = IdempotenceKey.initial(),
    )
    return sak
}

private suspend fun HttpClient.hentSaker(
    virksomhetsnumre: List<String> = listOf("42"),
    sortering: BrukerAPI.SakSortering = BrukerAPI.SakSortering.NYESTE,
    sakstyper: List<String>? = null,
    tekstsoek: String? = null,
    offset: Int? = null,
    limit: Int? = null,
) = querySakerJson(
    virksomhetsnumre = virksomhetsnumre,
    sakstyper = sakstyper,
    tekstsoek = tekstsoek,
    offset = offset,
    limit = limit,
    sortering = sortering
)