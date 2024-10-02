package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*


class TilleggsinformasjonSakTests : DescribeSpec({
    val (brukerRepository, engine) = setupRepoOgEngine()

    describe("Sak med tilleggsinformasjon null") {
        val sak = brukerRepository.nySakHendelse(tilleggsinformasjon = null)

        it ("Tilleggsinformasjon skal være null") {
            val sakRespons = engine.sakById(sak.sakId)
            val tilleggsinformasjon = sakRespons.getTypedContent<String?>("$.sakById.sak.tilleggsinformasjon")
            tilleggsinformasjon shouldBe null
        }

        it("burde få ny tilleggsinformasjon med idempotensnøkkel null") {
            brukerRepository.tilleggsinformasjonHendelse(
                tilleggsinformasjon = "tilleggsinformasjon",
                sakId = sak.sakId,
                grupperingsid = sak.grupperingsid,
                idempotenceKey = null
            )
            val sakRespons = engine.sakById(sak.sakId)
            val tilleggsinformasjon = sakRespons.getTypedContent<String?>("$.sakById.sak.tilleggsinformasjon")
            tilleggsinformasjon shouldBe "tilleggsinformasjon"
        }

        it("burde få ny tilleggsinformasjon med idempotensnøkkel") {
            brukerRepository.tilleggsinformasjonHendelse(
                tilleggsinformasjon = "ny tilleggsinformasjon",
                sakId = sak.sakId,
                grupperingsid = sak.grupperingsid,
                idempotenceKey = "idempotensnøkkel"
            )
            val sakRespons = engine.sakById(sak.sakId)
            val tilleggsinformasjon = sakRespons.getTypedContent<String?>("$.sakById.sak.tilleggsinformasjon")
            tilleggsinformasjon shouldBe "ny tilleggsinformasjon"
        }
    }
})

private suspend fun BrukerRepository.tilleggsinformasjonHendelse(
    tilleggsinformasjon: String?,
    sakId: UUID,
    grupperingsid: String,
    idempotenceKey: String?
) =
    HendelseModel.TilleggsinformasjonSak(
        tilleggsinformasjon = tilleggsinformasjon,
        sakId = sakId,
        grupperingsid = grupperingsid,
        merkelapp = "tag",
        virksomhetsnummer = "42",
        hendelseId = UUID.randomUUID(),
        idempotenceKey = idempotenceKey,
        produsentId = "1",
        kildeAppNavn = "1",
    ).also{oppdaterModellEtterHendelse(it)}

private suspend fun BrukerRepository.nySakHendelse(tilleggsinformasjon: String?) = HendelseModel.SakOpprettet(
    virksomhetsnummer = "42",
    merkelapp = "tag",
    nesteSteg = null,
    mottakere = listOf(HendelseModel.AltinnMottaker("5441", "1", "42")),
    oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
    grupperingsid = UUID.randomUUID().toString(),
    hardDelete = null,
    hendelseId = UUID.randomUUID(),
    produsentId = "1",
    kildeAppNavn = "1",
    mottattTidspunkt = OffsetDateTime.now(),
    lenke = null,
    sakId = UUID.randomUUID(),
    tittel = "foo",
    tilleggsinformasjon = tilleggsinformasjon
).also{oppdaterModellEtterHendelse(it)}

private fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)
    val engine = ktorBrukerTestServer(
        altinn = AltinnStub(
            "0".repeat(11) to BrukerModel.Tilganger(
                tjenestetilganger = listOf(
                    BrukerModel.Tilgang.Altinn("42", "5441", "1"),
                    BrukerModel.Tilgang.Altinn("43", "5441", "1")
                ),
            )
        ),
        brukerRepository = brukerRepository,
    )
    return Pair(brukerRepository, engine)
}

private fun TestApplicationEngine.sakById(sakId: UUID) = brukerApi(
    GraphQLRequest(
        """
                query sakById(${"$"}id: ID!) {
                    sakById(id: ${"$"}id) {
                        sak {
                            id
                            tilleggsinformasjon
                            merkelapp
                            virksomhet {
                                virksomhetsnummer                                
                            }
                            sisteStatus {
                                tidspunkt
                            }
                            frister
                            oppgaver {
                                tilstand
                            }
                            tidslinje {
                                __typename
                            }
                        }
                    }
                }
                """.trimIndent(),
        "sakById",
        mapOf("id" to sakId)
    )
)