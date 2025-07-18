package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BrukerRepositorySearchTest {

    @Test
    fun `BrukerRepositoryImpl#hentSaker`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        brukerRepository.insertSak("1", "Refusjon graviditet - Gerd - 112233")
        brukerRepository.insertSak("2", "Refusjon kronisk syk - Gerd - 112233")
        brukerRepository.insertSak("3", "Refusjon kronisk syk - Gerd - 223344")
        brukerRepository.insertSak("4", "Refusjon kronisk syk - Per - 123456")
        brukerRepository.insertSak("5", "Sykemelding - Per - 123456")
        brukerRepository.insertSak("6", "Sykemelding - Gerd - 123456")
        brukerRepository.insertSak("7", "Sykemelding - Pål - 111222")
        brukerRepository.insertSak("8", "Litt rare symboler // \\x % _")
        brukerRepository.insertSak("9", "Inntektsmelding for Forstandig Avarisk (07.03.07)")
        brukerRepository.insertSak("10", "Sykepenger for JOVIAL KJEDEKOLLISJON (f. 064294)")
        brukerRepository.insertSak("11", "Inntektsmelding for Fredfull Jakke (01.04.95)")
        brukerRepository.insertSak("12", "Sykepenger for Fredfull Jakke (f. 010495)")
        brukerRepository.insertSak("13", "Inntektsmelding for Fredfull Jakke f. 010495")

        mapOf(
            null to listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"),
            "" to listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"),
            " " to listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"),
            "zzzzz" to listOf(),
            "gravid" to listOf("1"),
            "    gravid    " to listOf("1"),
            " \tgravid    " to listOf("1"),
            "GRAVID" to listOf("1"),
            "graviditet" to listOf("1"),
            "Graviditet" to listOf("1"),
            "GRAVIDITET" to listOf("1"),
            "11" to listOf("1", "2", "7"),
            "22" to listOf("1", "2", "3", "7"),
            "33" to listOf("1", "2", "3"),
            "123456" to listOf("4", "5", "6"),
            "112233" to listOf("1", "2"),
            "gravid gerd" to listOf("1"),
            "gravid,gerd" to listOf("1"),
            "gravid, gerd" to listOf("1"),
            "   gravid    gerd   " to listOf("1"),
            "gerd gravid" to listOf("1"),
            "gerd\tgravid" to listOf("1"),
            "gerd\t \ngravid\n \r" to listOf("1"),
            "graviditet gerd" to listOf("1"),
            "gerd graviditet" to listOf("1"),
            "syk per" to listOf("4", "5"),
            "gravid per" to listOf(),
            "graviditet per" to listOf(),
            "per gravid" to listOf(),
            "112233 gerd" to listOf("1", "2"),
            "112233 kronisk" to listOf("2"),
            "kronisk 112233" to listOf("2"),
            "1223 kronisk" to listOf("2", "4"),
            "kron 1223" to listOf("2", "4"),
            "112233 gerd gravid" to listOf("1"),
            "gerd 112233 gravid" to listOf("1"),
            "gravid gerd 112233" to listOf("1"),
            "112233 gerd syk" to listOf("2"),
            "syk 112233 gerd" to listOf("2"),
            "syk gerd 112233" to listOf("2"),
            "syk gerd 11%33" to listOf(), // '%' from user is not interpreted as SQL wildcard
            "syk gerd 11_233" to listOf(), // '_' from user is not interpreted as SQL wildcard
            "syk gerd \\11233" to listOf(), // '\\' from user is not interpreted as SQL escape
            "PÅL" to listOf("7"),
            "pål" to listOf("7"),
            "pal" to listOf(),
            "\\" to listOf("8"),
            "\\x" to listOf("8"),
            "_" to listOf("8"),
            "%" to listOf("8"),
            "07.03.07" to listOf("9"),
            "070307" to listOf("9"),
            "064294" to listOf("10"),
            "06.42.94" to listOf("10"),
            "010495" to listOf("11", "12", "13"),
            "01.04.95" to listOf("11", "12", "13")
            // Other test cases?
            // - turkish i where upper-lower round-trip is not identity function
        ).forEach { (query, result) ->
            val expected = result.map { uuid(it) }.sorted()
            assertEquals(expected, brukerRepository.search(query).sorted(), "Assert failed for $query")
        }
    }
}


private suspend fun BrukerRepositoryImpl.search(query: String?): List<UUID> =
    hentSaker(
        fnr = "1",
        virksomhetsnummer = listOf("1"),
        altinnTilganger = AltinnTilganger(
            harFeil = false,
            tilganger = listOf(
                AltinnTilgang(
                    orgNr = "1",
                    tilgang = "1:1",
                )
            )
        ),
        tekstsoek = query,
        sakstyper = null,
        offset = 0,
        limit = 1000_000,
        oppgaveTilstand = null,
        oppgaveFilter = null,
        sortering = BrukerAPI.SakSortering.NYESTE
    )
        .saker
        .map { it.sakId }

private suspend fun BrukerRepositoryImpl.insertSak(id: String, tekst: String) {
    val sak = sakOpprettet(
        virksomhetsnummer = "1",
        merkelapp = "",
        sakId = uuid(id),
        grupperingsid = uuid(id).toString(),
        mottakere = listOf(HendelseModel.AltinnMottaker("1", "1", "1")),
        tittel = tekst,
        mottattTidspunkt = OffsetDateTime.now(),
    )
    nyStatusSak(
        sak = sak,
        virksomhetsnummer = "1",
        status = HendelseModel.SakStatus.MOTTATT,
        overstyrStatustekstMed = null,
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = "x",
    )
}
