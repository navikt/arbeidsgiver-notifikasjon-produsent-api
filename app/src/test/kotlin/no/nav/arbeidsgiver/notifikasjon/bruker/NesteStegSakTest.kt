package no.nav.arbeidsgiver.notifikasjon.bruker

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
import kotlin.test.assertNull


class NesteStegSakTest {

    @Test
    fun `Sak med nesteSteg null`() = withTestDatabase(Bruker.databaseConfig) { database ->
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
            val sak = brukerRepository.nySakHendelse(nesteSteg = null)
            with(client.sakById(sak.sakId)) {
                assertNull(getTypedContent<String?>("$.sakById.sak.nesteSteg"))
            }

            brukerRepository.nesteStegHendelse(
                nesteSteg = "nesteSteg",
                sakId = sak.sakId,
                grupperingsid = sak.grupperingsid,
                idempotenceKey = null
            )
            with(client.sakById(sak.sakId)) {
                assertEquals("nesteSteg", getTypedContent<String?>("$.sakById.sak.nesteSteg"))
            }

            brukerRepository.nesteStegHendelse(
                nesteSteg = "nytt steg",
                sakId = sak.sakId,
                grupperingsid = sak.grupperingsid,
                idempotenceKey = "idempotensnøkkel"
            )
            with(client.sakById(sak.sakId)) {
                assertEquals("nytt steg", getTypedContent<String?>("$.sakById.sak.nesteSteg"))
            }
        }
    }
}

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
    ).also { oppdaterModellEtterHendelse(it) }

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
).also { oppdaterModellEtterHendelse(it) }

private suspend fun HttpClient.sakById(sakId: UUID) = brukerApi(
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