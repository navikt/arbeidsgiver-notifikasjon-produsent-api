package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*

class QuerySakTests : DescribeSpec({
    val fallbackTimeNotUsed = OffsetDateTime.parse("2020-01-01T01:01:01Z")



    describe("Query.sakById") {
        val (brukerRepository, engine) = setupRepoOgEngine()
        val sak1 = brukerRepository.sakOpprettet(
            virksomhetsnummer = "42",
            merkelapp = "tag",
            mottakere = listOf(HendelseModel.AltinnMottaker("5441", "1", "42")),
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
        )
        val sak2 = brukerRepository.sakOpprettet(
            virksomhetsnummer = "42",
            merkelapp = "tag",
            lenke = null,
            mottakere = listOf(HendelseModel.AltinnMottaker("5441", "1", "42")),
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
        )

        it("sak1 response inneholder riktig data") {
            val response = engine.sakById(sak1.sakId)
            val sak = response.getTypedContent<BrukerAPI.Sak>("sakById/sak")
            sak.id shouldBe sak1.sakId
            sak.merkelapp shouldBe "tag"
            sak.lenke shouldBe sak1.lenke
            sak.tittel shouldBe sak1.tittel
            sak.virksomhet.virksomhetsnummer shouldBe sak1.virksomhetsnummer
            sak.sisteStatus.tekst shouldBe "Mottatt"
            sak.sisteStatus.tidspunkt shouldBe sak1.opprettetTidspunkt(fallbackTimeNotUsed)
        }

        it("sak2 response inneholder riktig data") {
            val response = engine.sakById(sak2.sakId)
            val sak = response.getTypedContent<BrukerAPI.Sak>("sakById/sak")
            sak.id shouldBe sak2.sakId
            sak.merkelapp shouldBe "tag"
            sak.lenke shouldBe sak2.lenke
            sak.tittel shouldBe sak2.tittel
            sak.virksomhet.virksomhetsnummer shouldBe sak2.virksomhetsnummer
            sak.sisteStatus.tekst shouldBe "Mottatt"
            sak.sisteStatus.tidspunkt shouldBe sak2.opprettetTidspunkt(fallbackTimeNotUsed)
        }

        it("bogus request response inneholder riktig data") {
            val response = engine.sakById(UUID.randomUUID())
            val node = response.getTypedContent<JsonNode>("sakById")
            node.get("sak").isNull shouldBe true
            node.get("feilAltinn").booleanValue() shouldBe false
        }
    }

    describe("Query.sakByGrupperingsid") {
        val (brukerRepository, engine) = setupRepoOgEngine()
        val sak1 = brukerRepository.sakOpprettet(
            virksomhetsnummer = "42",
            merkelapp = "tag",
            mottakere = listOf(HendelseModel.AltinnMottaker("5441", "1", "42")),
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
        )
        val sak2 = brukerRepository.sakOpprettet(
            virksomhetsnummer = "42",
            merkelapp = "tag",
            lenke = null,
            mottakere = listOf(HendelseModel.AltinnMottaker("5441", "1", "42")),
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
        )

        it("sak1 response inneholder riktig data") {
            val response = engine.sakByGrupperingsid(sak1.grupperingsid, sak1.merkelapp)
            val sak = response.getTypedContent<BrukerAPI.Sak>("sakByGrupperingsid/sak")
            sak.id shouldBe sak1.sakId
            sak.merkelapp shouldBe "tag"
            sak.lenke shouldBe sak1.lenke
            sak.tittel shouldBe sak1.tittel
            sak.virksomhet.virksomhetsnummer shouldBe sak1.virksomhetsnummer
            sak.sisteStatus.tekst shouldBe "Mottatt"
            sak.sisteStatus.tidspunkt shouldBe sak1.opprettetTidspunkt(fallbackTimeNotUsed)
        }

        it("sak2 response inneholder riktig data") {
            val response = engine.sakByGrupperingsid(sak2.grupperingsid, sak2.merkelapp)
            val sak = response.getTypedContent<BrukerAPI.Sak>("sakByGrupperingsid/sak")
            sak.id shouldBe sak2.sakId
            sak.merkelapp shouldBe "tag"
            sak.lenke shouldBe sak2.lenke
            sak.tittel shouldBe sak2.tittel
            sak.virksomhet.virksomhetsnummer shouldBe sak2.virksomhetsnummer
            sak.sisteStatus.tekst shouldBe "Mottatt"
            sak.sisteStatus.tidspunkt shouldBe sak2.opprettetTidspunkt(fallbackTimeNotUsed)
        }

        it("bogus request response inneholder riktig data") {
            val response = engine.sakByGrupperingsid("foo", "bar")
            val node = response.getTypedContent<JsonNode>("sakByGrupperingsid")
            node.get("sak").isNull shouldBe true
            node.get("feilAltinn").booleanValue() shouldBe false
        }
    }
})

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
                            tidslinje {
                                __typename
                                ...on OppgaveTidslinjeElement {
                                    id
                                    tekst
                                    tilstand
                                    opprettetTidspunkt
                                    paaminnelseTidspunkt
                                    utgaattTidspunkt
                                    utfoertTidspunkt
                                    frist
                                }
                                ...on BeskjedTidslinjeElement {
                                    id
                                    tekst
                                    opprettetTidspunkt
                                }
                            }
                        }
                        feilAltinn
                    }
                }
                """.trimIndent(),
        "sakById",
        mapOf("id" to sakId)
    )
)

private fun TestApplicationEngine.sakByGrupperingsid(grupperingsid: String, merkelapp: String) = brukerApi(
    GraphQLRequest(
        """
                query sakByGrupperingsid(${"$"}grupperingsid: String!, ${"$"}merkelapp: String!) {
                    sakByGrupperingsid(grupperingsid: ${"$"}grupperingsid, merkelapp: ${"$"}merkelapp) {
                        sak {
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
                            tidslinje {
                                __typename
                                ...on OppgaveTidslinjeElement {
                                    id
                                    tekst
                                    tilstand
                                    opprettetTidspunkt
                                    paaminnelseTidspunkt
                                    utgaattTidspunkt
                                    utfoertTidspunkt
                                    frist
                                }
                                ...on BeskjedTidslinjeElement {
                                    id
                                    tekst
                                    opprettetTidspunkt
                                }
                            }
                        }
                        feilAltinn
                    }
                }
                """.trimIndent(),
        "sakByGrupperingsid",
        mapOf(
            "grupperingsid" to grupperingsid,
            "merkelapp" to merkelapp
        )
    )
)