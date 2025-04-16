package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test
import kotlin.test.assertTrue

private val tilgang1 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "11111",
    serviceCode = "1",
    serviceEdition = "1"
)

class SakstyperQueryIngenTilgangerTest {


    @Test
    fun `ingen tilganger gir ingen saker`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val repo = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = repo,
            altinnTilgangerService = AltinnTilgangerServiceStub()
        ) {
            assertTrue(client.querySakstyper().isEmpty())

            repo.sakOpprettet(
                virksomhetsnummer = tilgang1.virksomhetsnummer,
                mottakere = listOf<HendelseModel.Mottaker>(element = tilgang1),
            ).also {
                repo.nyStatusSak(
                    sak = it,
                    idempotensKey = IdempotenceKey.initial(),
                )
            }

            assertTrue(client.querySakstyper().isEmpty())
        }

    }
}

private suspend fun HttpClient.querySakstyper(): Set<String> =
    querySakstyperJson()
        .getTypedContent<List<String>>("$.sakstyper.*.navn")
        .toSet()
