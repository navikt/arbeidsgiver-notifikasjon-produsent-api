package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakStatus
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakStatus.FERDIG
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilgang
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepository
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.TilgangerServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

class QuerySakerTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            tilgangerService = TilgangerServiceImpl(
                altinn = AltinnStub("0".repeat(11) to Tilganger(
                    tjenestetilganger = listOf(Tilgang.Altinn("42", "5441", "1")),
                    listOf(),
                    listOf(),
                )),
                altinnRolleService = mockk(relaxed = true),
            ),
            enhetsregisteret = EnhetsregisteretStub("42" to "el virksomhete"),
            brukerRepository = brukerRepository,
            kafkaProducer = mockk()
        )
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

        context("paginering med offset og limit angitt") {
            val eldsteId = uuid("2")
            val mellomsteId = uuid("1")
            val nyesteId = uuid("3")

            brukerRepository.opprettSakMedTidspunkt(mellomsteId, Duration.ofHours(2))
            brukerRepository.opprettSakMedTidspunkt(eldsteId, Duration.ofHours(1))
            brukerRepository.opprettSakMedTidspunkt(nyesteId, Duration.ofHours(3))

            it("nyeste sak først") {
                val response = engine.hentSaker(offset = 0, limit = 1)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe nyesteId
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("mellomste sak ved offset 1") {
                val response = engine.hentSaker(offset = 1, limit = 1)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe mellomsteId
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("eldste sak ved offset 2") {
                val response = engine.hentSaker(offset = 2, limit = 1)
                response.getTypedContent<BrukerAPI.Sak>("saker/saker/0").id shouldBe eldsteId
                response.getTypedContent<Int>("saker/totaltAntallSaker") shouldBe 3
            }

            it("utenfor offset") {
                val response = engine.hentSaker(offset = 3, limit = 1)
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
    )
    oppdaterModellEtterHendelse(sakOpprettet)
    oppdaterModellEtterHendelse(NyStatusSak(
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
    ))
    return sakOpprettet
}

private suspend fun BrukerRepository.opprettSakMedTidspunkt(
    sakId: UUID,
    shift: Duration,
) {
    val mottattTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
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
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
    )
    val status = NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = sak.virksomhetsnummer,
        produsentId = sak.produsentId,
        kildeAppNavn = sak.kildeAppNavn,
        sakId = sak.sakId,
        status = SakStatus.MOTTATT,
        overstyrStatustekstMed = "noe",
        mottattTidspunkt = mottattTidspunkt.plus(shift),
        idempotensKey = IdempotenceKey.initial(),
        oppgittTidspunkt = null,
    )
    oppdaterModellEtterHendelse(sak)
    oppdaterModellEtterHendelse(status)
}

fun TestApplicationEngine.hentSaker(
    tekstsoek: String? = null,
    offset: Int? = null,
    limit: Int? = null,
) = brukerApi(GraphQLRequest("""
    query hentSaker(${'$'}virksomhetsnummer: String!, ${'$'}tekstsoek: String, ${'$'}offset: Int, ${'$'}limit: Int){
        saker(virksomhetsnummer: ${'$'}virksomhetsnummer, tekstsoek: ${'$'}tekstsoek, offset: ${'$'}offset, limit: ${'$'}limit) {
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
            }
            feilAltinn
            totaltAntallSaker
        }
    }
    """.trimIndent(),
    "hentSaker",
    mapOf(
        "virksomhetsnummer" to "42",
        "tekstsoek" to tekstsoek,
        "offset" to offset,
        "limit" to limit,
    )
))