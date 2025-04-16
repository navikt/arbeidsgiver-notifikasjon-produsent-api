package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.virksomhetsnummer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val tilgang1 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "11111",
    serviceCode = "1",
    serviceEdition = "1"
)
private val tilgang2 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "22222",
    serviceCode = "1",
    serviceEdition = "1"
)
private val ikkeTilgang3 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "33333",
    serviceCode = "2",
    serviceEdition = "1"
)
private val ikkeTilgang4 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "44444",
    serviceCode = "3",
    serviceEdition = "1"
)


class SakstyperQueryTest {
    @Test
    fun `bruker har diverse tilganger`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(tilgang1, tilgang2).map {
                        AltinnTilgang(it.virksomhetsnummer, it.serviceCode + ":" + it.serviceEdition)
                    },
                )
            }
        ) {
            val skalSe = mutableSetOf<String>()
            val skalIkkeSe = mutableSetOf<String>()

            "merkelapp1".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, tilgang1)
                skalSe += merkelapp
            }

            "merkelapp2".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, ikkeTilgang3)
                skalIkkeSe += merkelapp
            }

            "merkelapp3".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, tilgang1)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang3)
                skalSe += merkelapp
            }

            "merkelapp4".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, tilgang1)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang3)
                skalSe += merkelapp
            }

            "merkelapp5".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, ikkeTilgang3)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang4)
                skalIkkeSe += merkelapp
            }

            "merkelapp6".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, tilgang1)
                brukerRepository.opprettSak(merkelapp, tilgang2)
                brukerRepository.opprettSak(merkelapp, tilgang2)
                skalSe += merkelapp
            }

            "merkelapp7".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, tilgang1)
                brukerRepository.opprettSak(merkelapp, tilgang2)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang4)
                skalSe += merkelapp
            }

            "merkelapp8".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, tilgang2)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang3)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang4)
                skalSe += merkelapp
            }

            "merkelapp9".let { merkelapp ->
                brukerRepository.opprettSak(merkelapp, ikkeTilgang3)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang3)
                brukerRepository.opprettSak(merkelapp, ikkeTilgang4)
                skalIkkeSe += merkelapp
            }

            val merkelapper = client.querySakstyper()

            assertEquals(skalSe, merkelapper)
            skalIkkeSe.forEach {
                assertTrue(it !in merkelapper)
            }
        }
    }
}

private suspend fun HttpClient.querySakstyper() =
    querySakstyperJson()
        .getTypedContent<List<String>>("$.sakstyper.*.navn")
        .toSet()

private suspend fun BrukerRepository.opprettSak(
    merkelapp: String,
    mottaker: HendelseModel.Mottaker,
) = sakOpprettet(
    virksomhetsnummer = mottaker.virksomhetsnummer,
    merkelapp = merkelapp,
    mottakere = listOf(mottaker),
).also { sak ->
    nyStatusSak(
        sak,
        idempotensKey = IdempotenceKey.initial(),
    )
}
