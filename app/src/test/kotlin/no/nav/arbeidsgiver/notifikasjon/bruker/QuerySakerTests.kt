package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.SakSortering.OPPRETTET
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

class QuerySakerTests : DescribeSpec({
    val fallbackTimeNotUsed = OffsetDateTime.parse("2020-01-01T01:01:01Z")

    describe("Query.saker") {
        context("med sak opprettet men ingen status") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val sakOpprettet = brukerRepository.sakOpprettet(
                virksomhetsnummer = "42",
                merkelapp = "tag",
                mottakere = listOf(AltinnMottaker("5441", "1", "42")),
                oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
                mottattTidspunkt = OffsetDateTime.now(),
                tilleggsinformasjon = "tilleggsinformasjon"
            )

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val sak = response.getTypedContent<BrukerAPI.Sak>("saker/saker/0")
                sak.id shouldBe sakOpprettet.sakId
                sak.merkelapp shouldBe "tag"
                sak.lenke shouldBe sakOpprettet.lenke
                sak.tittel shouldBe sakOpprettet.tittel
                sak.virksomhet.virksomhetsnummer shouldBe sakOpprettet.virksomhetsnummer
                sak.sisteStatus.tekst shouldBe "Mottatt"
                sak.sisteStatus.tidspunkt shouldBe sakOpprettet.opprettetTidspunkt(fallbackTimeNotUsed)
                sak.tilleggsinformasjon shouldBe "tilleggsinformasjon"
            }
        }

        context("med sak og status") {
            val (brukerRepository, engine) = setupRepoOgEngine()
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

            val response = engine.hentSaker(
                virksomhetsnumre = listOf("43"),
            )

            it("response inneholder riktig data for sak") {
                val sak = response.getTypedContent<BrukerAPI.Sak>("saker/saker/0")
                sak.id shouldBe sakOpprettet.sakId
                sak.merkelapp shouldBe "tag"
                sak.lenke shouldBe sakOpprettet.lenke
                sak.tittel shouldBe sakOpprettet.tittel
                sak.virksomhet.virksomhetsnummer shouldBe sakOpprettet.virksomhetsnummer
                sak.sisteStatus.tekst shouldBe "noe"
            }
        }

        context("paginering med offset og limit angitt sortert på oppdatert") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val forventetRekkefoelge = listOf(
                uuid("3"),
                uuid("1"),
                uuid("4"),
            )

            brukerRepository.opprettSakMedTidspunkt(forventetRekkefoelge[0], Duration.ofHours(1), Duration.ofHours(5))
            brukerRepository.opprettSakMedTidspunkt(forventetRekkefoelge[1], Duration.ofHours(2), Duration.ofHours(4))
            brukerRepository.opprettSakMedTidspunkt(forventetRekkefoelge[2], Duration.ofHours(3))

            it("saksrekkefølge er korrekt innenfor page") {
                val response = engine.hentSaker(offset = 0, limit = 3)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[0]
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/1").id shouldBe forventetRekkefoelge[1]
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/2").id shouldBe forventetRekkefoelge[2]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("sist oppdaterte sak først") {
                val response = engine.hentSaker(offset = 0, limit = 1)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[0]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("mellomste sak ved offset 1") {
                val response = engine.hentSaker(offset = 1, limit = 1)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[1]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("eldste sak ved offset 2") {
                val response = engine.hentSaker(offset = 2, limit = 1)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[2]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("utenfor offset") {
                val response = engine.hentSaker(offset = 3, limit = 1)
                response.getTypedContent<List<Any>>("saker/saker") should beEmpty()
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("offset og limit 0 gir fortsatt totalt antall saker") {
                val response = engine.hentSaker(offset = 0, limit = 0)
                response.getTypedContent<List<Any>>("saker/saker") should beEmpty()
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }
        }

        context("paginering med offset og limit angitt sortert på opprettet") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val forventetRekkefoelge = listOf(
                uuid("3"),
                uuid("1"),
                uuid("4"),
            )

            brukerRepository.opprettSakMedTidspunkt(forventetRekkefoelge[0], Duration.ofHours(3))
            brukerRepository.opprettSakMedTidspunkt(forventetRekkefoelge[1], Duration.ofHours(2), Duration.ofHours(4))
            brukerRepository.opprettSakMedTidspunkt(forventetRekkefoelge[2], Duration.ofHours(1), Duration.ofHours(5))

            it("saksrekkefølge er korrekt innenfor page") {
                val response = engine.hentSaker(offset = 0, limit = 3, sortering = OPPRETTET)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[0]
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/1").id shouldBe forventetRekkefoelge[1]
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/2").id shouldBe forventetRekkefoelge[2]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("sist oppdaterte sak først") {
                val response = engine.hentSaker(offset = 0, limit = 1, sortering = OPPRETTET)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[0]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("mellomste sak ved offset 1") {
                val response = engine.hentSaker(offset = 1, limit = 1, sortering = OPPRETTET)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[1]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("eldste sak ved offset 2") {
                val response = engine.hentSaker(offset = 2, limit = 1, sortering = OPPRETTET)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe forventetRekkefoelge[2]
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("utenfor offset") {
                val response = engine.hentSaker(offset = 3, limit = 1, sortering = OPPRETTET)
                response.getTypedContent<List<Any>>("saker/saker") should beEmpty()
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }
        }

        context("tekstsøk") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val sak1 = brukerRepository.opprettSakForTekstsøk("pippi langstrømpe er friskmeldt", MOTTATT, "herr nilson er syk")
            val sak2 = brukerRepository.opprettSakForTekstsøk("donald duck er permittert", FERDIG, "saken er avblåst")

            it("søk på tittel returnerer riktig sak") {
                val response = engine.hentSaker(tekstsoek = "pippi")
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 1
                saker.first().id shouldBe sak1.sakId
            }

            it("søk på status returnerer riktig sak") {
                val response = engine.hentSaker(tekstsoek = "ferdig")
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 1
                saker.first().id shouldBe sak2.sakId
            }

            it("søk på statustekst returnerer riktig sak") {
                val response = engine.hentSaker(tekstsoek = "avblåst")
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 1
                saker.first().id shouldBe sak2.sakId
            }

            brukerRepository.nyStatusSak(
                sak = sak1,
                status = FERDIG,
                overstyrStatustekstMed = "i boks med sløyfe på",
                idempotensKey = IdempotenceKey.initial(),
            )

            it("søk på opprinnelig statustekst returnerer riktig sak") {
                val response = engine.hentSaker(tekstsoek = "nilson")
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 1
                saker.first().id shouldBe sak1.sakId
            }

            it("søk på ny statustekst returnerer riktig sak") {
                val response = engine.hentSaker(tekstsoek = "sløyfe")
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 1
                saker.first().id shouldBe sak1.sakId
            }
        }

        context("søk på tvers av virksomheter") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            val sak1 = brukerRepository.opprettSak(uuid("1"), "42", AltinnMottaker("5441", "1", "42"))
            val sak2 = brukerRepository.opprettSak(uuid("2"), "43", AltinnRessursMottaker("43", "nav_test_foo-ressursid"))

            it("hentSaker med tom liste av virksomhetsnumre gir tom liste") {
                val response = engine.hentSaker(virksomhetsnumre = listOf())
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 0
            }

            it("hentSaker med liste av virksomhetsnumre=42 gir riktig sak") {
                val response = engine.hentSaker(listOf("42"))
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 1
                saker.first().id shouldBe sak1.sakId
            }

            it("hentSaker med liste av virksomhetsnumre=43 gir riktig sak") {
                val response = engine.hentSaker(listOf("43"))
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 1
                saker.first().id shouldBe sak2.sakId
            }

            it("hentSaker med liste av virksomhetsnumre=42,43 gir riktig sak") {
                val response = engine.hentSaker(virksomhetsnumre = listOf("42", "43"))
                val saker = response.getTypedContent<List<BrukerAPI.Sak>>("saker/saker")
                saker shouldHaveSize 2
            }
        }

        context("søk på type sak") {
            val (brukerRepository, engine) = setupRepoOgEngine()
            brukerRepository.opprettSak(uuid("1"), "42", AltinnMottaker("5441", "1", "42"), "merkelapp1") // tilgang til 42
            brukerRepository.opprettSak(uuid("2"), "43", AltinnRessursMottaker("43", "nav_test_foo-ressursid"), "merkelapp2") // tilgang til 43
            brukerRepository.opprettSak(uuid("3"), "44", AltinnMottaker("5441", "1", "44"), "merkelapp3") // ikke tilgang til 44
            brukerRepository.opprettSak(uuid("4"), "45", AltinnMottaker("5441", "1", "45"), "merkelapp1") // ikke tilgang til 45


            it("søk på null sakstyper returnere alle") {
                val response = engine.hentSaker(listOf("42", "43", "44", "45"))
                val saker = response.getTypedContent<List<UUID>>("$.saker.saker.*.id")
                saker shouldContainExactlyInAnyOrder listOf(uuid("1"), uuid("2"))

                val sakstyper = response.getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
                sakstyper shouldContainExactlyInAnyOrder listOf("merkelapp1", "merkelapp2")
            }

            it("søk på merkelapp1") {
                val response = engine.hentSaker(listOf("42", "43", "44", "45"), sakstyper = listOf("merkelapp1"))
                val saker = response.getTypedContent<List<UUID>>("$.saker.saker.*.id")
                saker shouldContainExactlyInAnyOrder listOf(uuid("1"))

                val sakstyper = response.getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
                sakstyper shouldContainExactlyInAnyOrder listOf("merkelapp1", "merkelapp2")
            }

            it("søk på merkelapp1 og merkelapp2") {
                val response = engine.hentSaker(listOf("42", "43", "44", "45"), sakstyper = listOf("merkelapp1", "merkelapp2"))
                val saker = response.getTypedContent<List<UUID>>("$.saker.saker.*.id")
                saker shouldContainExactlyInAnyOrder listOf(uuid("1"), uuid("2"))

                val sakstyper = response.getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
                sakstyper shouldContainExactlyInAnyOrder listOf("merkelapp1", "merkelapp2")
            }

            it("søk på merkelapp3") {
                val response = engine.hentSaker(listOf("42", "43", "44", "45"), sakstyper = listOf("merkelapp3"))
                val saker = response.getTypedContent<List<UUID>>("$.saker.saker.*.id")
                saker shouldContainExactlyInAnyOrder listOf()

                val sakstyper = response.getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
                sakstyper shouldContainExactlyInAnyOrder listOf("merkelapp1", "merkelapp2")
            }

            it("søk på tom liste") {
                val response = engine.hentSaker(listOf("42", "43", "44", "45"), sakstyper = listOf())
                val saker = response.getTypedContent<List<UUID>>("$.saker.saker.*.id")
                saker shouldContainExactlyInAnyOrder listOf()

                val sakstyper = response.getTypedContent<List<String>>("$.saker.sakstyper.*.navn")
                sakstyper shouldContainExactlyInAnyOrder listOf("merkelapp1", "merkelapp2")
            }
        }
    }
})

private fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)
    val engine = ktorBrukerTestServer(
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
    )
    return Pair(brukerRepository, engine)
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
) {
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

private fun TestApplicationEngine.hentSaker(
    virksomhetsnumre: List<String> = listOf("42"),
    sakstyper: List<String>? = null,
    tekstsoek: String? = null,
    offset: Int? = null,
    limit: Int? = null,
    sortering: BrukerAPI.SakSortering = BrukerAPI.SakSortering.OPPDATERT,
) = querySakerJson(
    virksomhetsnumre = virksomhetsnumre,
    sakstyper = sakstyper,
    tekstsoek = tekstsoek,
    offset = offset,
    limit = limit,
    sortering = sortering,
)