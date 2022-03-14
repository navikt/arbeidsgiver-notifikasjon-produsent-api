package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.SakStatus
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilgang
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
                altinn = AltinnStub("0".repeat(11) to listOf(Tilgang.Altinn("42", "5441", "1"))),
                altinnRolleService = mockk(relaxed = true),
            ),
            enhetsregisteret = EnhetsregisteretStub("42" to "el virksomhete"),
            brukerRepository = brukerRepository,
            kafkaProducer = mockk()
        )
    )

    describe("Query.saker") {
        val sakOpprettet = Hendelse.SakOpprettet(
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
        )
        val statusSak = Hendelse.NyStatusSak(
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

        context("med offset og limit angitt") {
            val eldsteId = uuid("2")
            val mellomsteId = uuid("1")
            val nyesteId = uuid("3")

            brukerRepository.opprettSakMedTidspunkt(mellomsteId, Duration.ofHours(2))
            brukerRepository.opprettSakMedTidspunkt(eldsteId, Duration.ofHours(1))
            brukerRepository.opprettSakMedTidspunkt(nyesteId, Duration.ofHours(3))

            it("nyeste sak først") {
                val sak = engine.hentSaker(offset = 0).getTypedContent<BrukerAPI.Sak>("saker/saker/0")
                sak.id shouldBe nyesteId
            }

            it("mellomste sak ved offset 1") {
                val sak = engine.hentSaker(offset = 1).getTypedContent<BrukerAPI.Sak>("saker/saker/0")
                sak.id shouldBe mellomsteId
            }

            it("eldste sak ved offset 2") {
                val sak = engine.hentSaker(offset = 2).getTypedContent<BrukerAPI.Sak>("saker/saker/0")
                sak.id shouldBe eldsteId
            }
        }
    }
})

private suspend fun BrukerRepository.opprettSakMedTidspunkt(
    sakId: UUID,
    shift: Duration,
) {
    val mottattTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
    val sak = Hendelse.SakOpprettet(
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
    )
    val status = Hendelse.NyStatusSak(
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
    offset: Int? = null,
    limit: Int? = null,
) = brukerApi(GraphQLRequest("""
    query hentSaker(${'$'}virksomhetsnummer: String!, ${'$'}offset: Int, ${'$'}limit: Int){
        saker(virksomhetsnummer: ${'$'}virksomhetsnummer, offset: ${'$'}offset, limit: ${'$'}limit) {
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
        }
    }
    """.trimIndent(),
    "hentSaker",
    mapOf(
        "virksomhetsnummer" to "42",
        "offset" to offset,
        "limit" to limit,
    )
))