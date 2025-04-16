package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Sakstype
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.MOTTATT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val tilgang1 = AltinnMottaker(virksomhetsnummer = "43", serviceCode = "5441", serviceEdition = "1")
private val tilgang2 = AltinnMottaker(virksomhetsnummer = "44", serviceCode = "5441", serviceEdition = "1")

class QuerySakerAntallMerkelapperTest {
    @Test
    fun `antall i sakstype (Query#saker)`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            altinnTilgangerService = AltinnTilgangerServiceStub(
                "0".repeat(11) to AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(tilgang1, tilgang2).map {
                        AltinnTilgang(
                            orgNr = it.virksomhetsnummer,
                            tilgang = "${it.serviceCode}:${it.serviceEdition}",
                        )
                    },
                )
            ),
            brukerRepository = brukerRepository,
        ) {
            for (merkelapp in listOf("merkelapp1", "merkelapp2")) {
                for (tittel in listOf("sykmelding", "refusjon")) {
                    brukerRepository.opprettSak(
                        tilgang = tilgang1,
                        merkelapp = merkelapp,
                        tittel = tittel,
                    )
                }
            }
            for (tittel in listOf("sykmelding", "refusjon")) {
                brukerRepository.opprettSak(
                    tilgang = tilgang2,
                    merkelapp = "merkelapp1",
                    tittel = tittel,
                )
            }

            // ingen virksomheter gir ingen sakstyper
            assertTrue(client.hentSakstyper(virksomhetsnumre = listOf()).isEmpty())

            // ingen virksomheter gir ingen sakstyper, selv med merkelapp valgt
            assertTrue(
                client.hentSakstyper(
                    virksomhetsnumre = listOf(),
                    sakstyper = listOf("merkelapp1")
                ).isEmpty()
            )

            // filter på virksomhet og merkelapp
            assertEquals(
                listOf(
                    Sakstype("merkelapp1", 2),
                    Sakstype("merkelapp2", 2),
                ),
                client.hentSakstyper(
                    virksomhetsnumre = listOf(tilgang1.virksomhetsnummer),
                    sakstyper = listOf("merkelapp1"),
                )
            )

            // filter på virksomhet1 og tekstsøk
            assertEquals(
                listOf(
                    Sakstype("merkelapp1", 1),
                    Sakstype("merkelapp2", 1)
                ),
                client.hentSakstyper(
                    virksomhetsnumre = listOf(tilgang1.virksomhetsnummer),
                    tekstsoek = "sykmelding"
                )
            )

            // filter på virksomhet2 og tekstsøk
            assertEquals(
                listOf(
                    Sakstype("merkelapp1", 1),
                ),
                client.hentSakstyper(
                    virksomhetsnumre = listOf(tilgang2.virksomhetsnummer),
                    tekstsoek = "sykmelding"
                )
            )

            // filter på virksomhet2 og tekstsøk
            assertEquals(
                listOf(
                    Sakstype("merkelapp1", 1),
                ),
                client.hentSakstyper(
                    virksomhetsnumre = listOf(tilgang2.virksomhetsnummer),
                    tekstsoek = "sykmelding",
                    sakstyper = listOf("merkelapp2")
                )
            )

            // ingen filter gir alt
            assertEquals(
                listOf(
                    Sakstype("merkelapp1", 4),
                    Sakstype("merkelapp2", 2)
                ),
                client.hentSakstyper()
            )
        }
    }
}

private suspend fun BrukerRepository.opprettSak(
    tilgang: AltinnMottaker,
    merkelapp: String,
    tittel: String,
): SakOpprettet {
    val sakId = UUID.randomUUID()
    val oppgittTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
    val sak = sakOpprettet(
        sakId = sakId,
        grupperingsid = sakId.toString(),
        virksomhetsnummer = tilgang.virksomhetsnummer,
        produsentId = "test",
        kildeAppNavn = "test",
        merkelapp = merkelapp,
        mottakere = listOf(tilgang),
        tittel = tittel,
        lenke = "#foo",
        oppgittTidspunkt = oppgittTidspunkt,
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
        tilleggsinformasjon = null
    )
    nyStatusSak(
        sak = sak,
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = sak.virksomhetsnummer,
        produsentId = sak.produsentId,
        kildeAppNavn = sak.kildeAppNavn,
        status = MOTTATT,
        overstyrStatustekstMed = "noe",
        mottattTidspunkt = oppgittTidspunkt,
        idempotensKey = IdempotenceKey.initial(),
        oppgittTidspunkt = null,
        hardDelete = null,
        nyLenkeTilSak = null,
    )
    return sak
}

private suspend fun HttpClient.hentSakstyper(
    virksomhetsnumre: List<String> = listOf(tilgang1.virksomhetsnummer, tilgang2.virksomhetsnummer),
    sakstyper: List<String>? = null,
    tekstsoek: String? = null,
): List<Sakstype> =
    querySakerJson(
        virksomhetsnumre = virksomhetsnumre,
        sakstyper = sakstyper,
        tekstsoek = tekstsoek,
    )
        .getTypedContent("$.saker.sakstyper")
