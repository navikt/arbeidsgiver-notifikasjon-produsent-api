package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class QuerySakTest {
    private val fallbackTimeNotUsed: OffsetDateTime = OffsetDateTime.parse("2020-01-01T01:01:01Z")

    @Test
    fun `Query#sakById`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "5441:1")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            val sak1 = brukerRepository.sakOpprettet(
                virksomhetsnummer = "42",
                merkelapp = "tag",
                nesteSteg = "foo",
                mottakere = listOf(HendelseModel.AltinnMottaker("5441", "1", "42")),
                oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
                mottattTidspunkt = OffsetDateTime.now(),
                tilleggsinformasjon = "tilleggsinformasjon1"
            )
            val sak2 = brukerRepository.sakOpprettet(
                virksomhetsnummer = "42",
                merkelapp = "tag",
                lenke = null,
                nesteSteg = null,
                mottakere = listOf(HendelseModel.AltinnMottaker("5441", "1", "42")),
                oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
                mottattTidspunkt = OffsetDateTime.now(),
                tilleggsinformasjon = "tilleggsinformasjon2"
            )

            // sak1 response inneholder riktig data
            with(client.sakById(sak1.sakId).getTypedContent<BrukerAPI.Sak>("sakById/sak")) {
                assertEquals(sak1.sakId, id)
                assertEquals("tag", merkelapp)
                assertEquals(sak1.lenke, lenke)
                assertEquals(sak1.tittel, tittel)
                assertEquals(sak1.nesteSteg, nesteSteg)
                assertEquals(sak1.virksomhetsnummer, virksomhet.virksomhetsnummer)
                assertEquals("Mottatt", sisteStatus.tekst)
                assertEquals(sak1.opprettetTidspunkt(fallbackTimeNotUsed), sisteStatus.tidspunkt)
                assertEquals("tilleggsinformasjon1", tilleggsinformasjon)
            }


            // sak2 response inneholder riktig data
            with(client.sakById(sak2.sakId).getTypedContent<BrukerAPI.Sak>("sakById/sak")) {
                assertEquals(sak2.sakId, id)
                assertEquals("tag", merkelapp)
                assertEquals(sak2.lenke, lenke)
                assertEquals(null, nesteSteg)
                assertEquals(sak2.tittel, tittel)
                assertEquals(sak2.virksomhetsnummer, virksomhet.virksomhetsnummer)
                assertEquals("Mottatt", sisteStatus.tekst)
                assertEquals(sak2.opprettetTidspunkt(fallbackTimeNotUsed), sisteStatus.tidspunkt)
                assertEquals("tilleggsinformasjon2", tilleggsinformasjon)
            }


            // bogus request response inneholder riktig data
            with(client.sakById(UUID.randomUUID()).getTypedContent<JsonNode>("sakById")) {
                assertEquals(true, get("sak").isNull)
                assertEquals(false, get("feilAltinn").booleanValue())
            }


        }
    }

    @Test
    fun `Query#sakByGrupperingsid`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("42", "5441:1"),
                        AltinnTilgang("43", "5441:1")
                    ),
                )
            ),
            brukerRepository = brukerRepository,
        ) {
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
                tilleggsinformasjon = "tilleggsinformasjon"
            )

            // sak1 response inneholder riktig data

            with(
                client.sakByGrupperingsid(
                    sak1.grupperingsid,
                    sak1.merkelapp
                ).getTypedContent<BrukerAPI.Sak>("sakByGrupperingsid/sak")
            ) {
                assertEquals(sak1.sakId, id)
                assertEquals("tag", merkelapp)
                assertEquals(sak1.lenke, lenke)
                assertEquals(null, nesteSteg)
                assertEquals(sak1.tittel, tittel)
                assertEquals(sak1.virksomhetsnummer, virksomhet.virksomhetsnummer)
                assertEquals("Mottatt", sisteStatus.tekst)
                assertEquals(sak1.opprettetTidspunkt(fallbackTimeNotUsed), sisteStatus.tidspunkt)
                assertEquals(null, tilleggsinformasjon)
            }

            // sak2 response inneholder riktig data
            with(
                client.sakByGrupperingsid(
                    sak2.grupperingsid,
                    sak2.merkelapp
                ).getTypedContent<BrukerAPI.Sak>("sakByGrupperingsid/sak")
            ) {
                assertEquals(sak2.sakId, id)
                assertEquals("tag", merkelapp)
                assertEquals(sak2.lenke, lenke)
                assertEquals(sak2.tittel, tittel)
                assertEquals(sak2.virksomhetsnummer, virksomhet.virksomhetsnummer)
                assertEquals("Mottatt", sisteStatus.tekst)
                assertEquals(sak2.opprettetTidspunkt(fallbackTimeNotUsed), sisteStatus.tidspunkt)
                assertEquals("tilleggsinformasjon", tilleggsinformasjon)
            }

            // bogus request response inneholder riktig data
            with(client.sakByGrupperingsid("foo", "bar").getTypedContent<JsonNode>("sakByGrupperingsid")) {
                assertEquals(true, get("sak").isNull)
                assertEquals(false, get("feilAltinn").booleanValue())
            }

        }
    }
}

private suspend fun HttpClient.sakById(sakId: UUID) = brukerApi(
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
                            nesteSteg
                            tilleggsinformasjon
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

private suspend fun HttpClient.sakByGrupperingsid(grupperingsid: String, merkelapp: String) = brukerApi(
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
                            nesteSteg
                            tilleggsinformasjon
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