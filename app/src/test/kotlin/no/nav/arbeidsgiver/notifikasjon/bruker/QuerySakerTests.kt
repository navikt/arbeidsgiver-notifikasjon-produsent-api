package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.SakSortering.OPPRETTET
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilgang
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.FERDIG
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

class QuerySakerTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        altinn = AltinnStub(
            "0".repeat(11) to Tilganger(
                tjenestetilganger = listOf(Tilgang.Altinn("42", "5441", "1")),
                listOf(),
                listOf(),
            )
        ),
        brukerRepository = brukerRepository,
    )

    describe("Query.saker") {
        val sakOpprettet = SakOpprettet(
            hendelseId = uuid("0"),
            virksomhetsnummer = "42",
            produsentId = "test",
            kildeAppNavn = "test",
            sakId = uuid("0"),
            grupperingsid = "42",
            merkelapp = "tag",
            mottakere = listOf(AltinnMottaker("5441", "1", "42")),
            tittel = "er det no sak",
            lenke = "#foo",
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
            hardDelete = null,
        )
        val statusSak = NyStatusSak(
            hendelseId = uuid("1"),
            virksomhetsnummer = sakOpprettet.virksomhetsnummer,
            produsentId = sakOpprettet.produsentId,
            kildeAppNavn = sakOpprettet.kildeAppNavn,
            sakId = sakOpprettet.sakId,
            status = SakStatus.MOTTATT,
            overstyrStatustekstMed = "noe",
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
            idempotensKey = IdempotenceKey.initial(),
            hardDelete = null,
            nyLenkeTilSak = null,
        )

        context("med sak opprettet men ingen status") {
            brukerRepository.oppdaterModellEtterHendelse(sakOpprettet)

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
                val saker = response.getTypedContent<List<Any>>("saker/saker")
                saker should beEmpty()
            }
        }

        context("med sak og status") {
            brukerRepository.oppdaterModellEtterHendelse(sakOpprettet)
            brukerRepository.oppdaterModellEtterHendelse(statusSak)

            val response = engine.hentSaker()

            it("response inneholder riktig data") {
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
        }

        context("paginering med offset og limit angitt sortert på opprettet") {
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
            val sak1 = brukerRepository.opprettSakForTekstsøk("pippi langstrømpe er friskmeldt")
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
        }
    }
})

private suspend fun BrukerRepository.opprettSakForTekstsøk(
    tittel: String,
    status: SakStatus = SakStatus.MOTTATT,
    overstyrStatustekst: String? = null,
): SakOpprettet {
    val sakOpprettet = SakOpprettet(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = "42",
        produsentId = "test",
        kildeAppNavn = "test",
        sakId = UUID.randomUUID(),
        grupperingsid = UUID.randomUUID().toString(),
        merkelapp = "tag",
        mottakere = listOf(AltinnMottaker("5441", "1", "42")),
        tittel = tittel,
        lenke = "#foo",
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    )
    oppdaterModellEtterHendelse(sakOpprettet)
    oppdaterModellEtterHendelse(
        NyStatusSak(
            hendelseId = UUID.randomUUID(),
            virksomhetsnummer = sakOpprettet.virksomhetsnummer,
            produsentId = sakOpprettet.produsentId,
            kildeAppNavn = sakOpprettet.kildeAppNavn,
            sakId = sakOpprettet.sakId,
            status = status,
            overstyrStatustekstMed = overstyrStatustekst,
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
            idempotensKey = IdempotenceKey.initial(),
            hardDelete = null,
            nyLenkeTilSak = null,
        )
    )
    return sakOpprettet
}

private suspend fun BrukerRepository.opprettSakMedTidspunkt(
    sakId: UUID,
    vararg shift: Duration,
) {
    val oppgittTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
    val sak = SakOpprettet(
        hendelseId = sakId,
        sakId = sakId,
        grupperingsid = sakId.toString(),
        virksomhetsnummer = "42",
        produsentId = "test",
        kildeAppNavn = "test",
        merkelapp = "tag",
        mottakere = listOf(AltinnMottaker("5441", "1", "42")),
        tittel = "er det no sak",
        lenke = "#foo",
        oppgittTidspunkt = oppgittTidspunkt,
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    ).also {
        oppdaterModellEtterHendelse(it)
    }
    shift.forEach {
        NyStatusSak(
            hendelseId = UUID.randomUUID(),
            virksomhetsnummer = sak.virksomhetsnummer,
            produsentId = sak.produsentId,
            kildeAppNavn = sak.kildeAppNavn,
            sakId = sak.sakId,
            status = SakStatus.MOTTATT,
            overstyrStatustekstMed = "noe",
            mottattTidspunkt = oppgittTidspunkt.plus(it),
            idempotensKey = IdempotenceKey.initial(),
            oppgittTidspunkt = null,
            hardDelete = null,
            nyLenkeTilSak = null,
        ).also { hendelse ->
            oppdaterModellEtterHendelse(hendelse)
        }
    }
}

private fun TestApplicationEngine.hentSaker(
    tekstsoek: String? = null,
    offset: Int? = null,
    limit: Int? = null,
    sortering: BrukerAPI.SakSortering = BrukerAPI.SakSortering.OPPDATERT,
) = brukerApi(
    GraphQLRequest(
        """
    query hentSaker(${'$'}virksomhetsnumre: [String!]!, ${'$'}tekstsoek: String, ${'$'}sortering: SakSortering!, ${'$'}offset: Int, ${'$'}limit: Int){
        saker(virksomhetsnumre: ${'$'}virksomhetsnumre, tekstsoek: ${'$'}tekstsoek, sortering: ${'$'}sortering, offset: ${'$'}offset, limit: ${'$'}limit) {
            saker {
                id
                tittel
                lenke
                merkelapp
                virksomhet {
                    navn
                    virksomhetsnummer
                }
                sisteStatus {
                    type
                    tekst
                    tidspunkt
                }
                frister
                oppgaver {
                    frist
                    tilstand
                    paaminnelseTidspunkt
                }
            }
            feilAltinn
            totaltAntallSaker
        }
    }
    """.trimIndent(),
        "hentSaker",
        mapOf(
            "virksomhetsnumre" to listOf("42"),
            "tekstsoek" to tekstsoek,
            "sortering" to sortering,
            "offset" to offset,
            "limit" to limit,
        )
    )
)