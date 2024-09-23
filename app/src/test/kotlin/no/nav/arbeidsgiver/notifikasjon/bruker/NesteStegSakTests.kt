package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*


class NesteStegSakTests : DescribeSpec({
    val (brukerRepository, engine) = setupRepoOgEngine()

    describe("Sak med nesteSteg null") {
        val sak = brukerRepository.nySakHendelse(nesteSteg = null)

        it ("Neste steg skal være null") {
            val sakRespons = engine.sakById(sak.sakId)
            val neste = sakRespons.getTypedContent<String?>("$.sakById.sak.nesteSteg")
            neste shouldBe null
        }

        it("burde få ny nesteSteg med idempotensnøkkel null") {
            brukerRepository.nesteStegHendelse(
                nesteSteg = "nesteSteg",
                sakId = sak.sakId,
                grupperingsid = sak.grupperingsid,
                idempotenceKey = null
            )
            val sakRespons = engine.sakById(sak.sakId)
            val neste = sakRespons.getTypedContent<String?>("$.sakById.sak.nesteSteg")
            neste shouldBe "nesteSteg"
        }

        it("burde få ny nesteSteg med idempotensnøkkel") {
            brukerRepository.nesteStegHendelse(
                nesteSteg = "nytt steg",
                sakId = sak.sakId,
                grupperingsid = sak.grupperingsid,
                idempotenceKey = "idempotensnøkkel"
            )
            val sakRespons = engine.sakById(sak.sakId)
            val neste = sakRespons.getTypedContent<String?>("$.sakById.sak.nesteSteg")
            neste shouldBe "nytt steg"
        }

    }
})

private suspend fun BrukerRepository.nesteStegHendelse(
    nesteSteg: String?,
    sakId: UUID,
    grupperingsid: String,
    idempotenceKey: String?
) =
    HendelseModel.NesteStegSak(
        nesteSteg = nesteSteg,
        sakId = sakId,
        grupperingsid = grupperingsid,
        merkelapp = "tag",
        virksomhetsnummer = "42",
        hendelseId = UUID.randomUUID(),
        idempotenceKey = idempotenceKey,
        produsentId = "1",
        kildeAppNavn = "1",
    ).also{oppdaterModellEtterHendelse(it)}

private suspend fun BrukerRepository.nySakHendelse(nesteSteg: String?) = HendelseModel.SakOpprettet(
    virksomhetsnummer = "42",
    merkelapp = "tag",
    nesteSteg = nesteSteg,
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
    tilleggsinformasjon = null
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
                            nesteSteg
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