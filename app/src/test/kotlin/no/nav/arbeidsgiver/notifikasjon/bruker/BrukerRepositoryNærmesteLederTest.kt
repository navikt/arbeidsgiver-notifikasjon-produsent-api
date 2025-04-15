package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BrukerRepositoryNærmesteLederTest {
    @Test
    fun `når mottar slett hendelse uten at det er noe å slette`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val narmesteLederFnr = "42"
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003"),
                fnr = "43",
                narmesteLederFnr = narmesteLederFnr,
                orgnummer = "44",
                aktivTom = LocalDate.now()
            )
        )

        assertEquals(emptyList(), database.hentAnsatte(narmesteLederFnr))
    }

    @Test
    fun `når mottar to forskjellige koblinger`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val narmesteLederId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = narmesteLederId,
                fnr = "1",
                narmesteLederFnr = "1",
                orgnummer = "1",
                aktivTom = null
            )
        )
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = narmesteLederId,
                fnr = "2",
                narmesteLederFnr = "2",
                orgnummer = "2",
                aktivTom = null
            )
        )

        assertEquals(emptyList(), database.hentAnsatte("1"))
        assertEquals(
            listOf(
                NærmesteLederFor(
                    ansattFnr = "2",
                    virksomhetsnummer = "2"
                )
            ),
            database.hentAnsatte("2")
        )
    }
}


data class NærmesteLederFor(
    val ansattFnr: String,
    val virksomhetsnummer: String,
)

private suspend fun Database.hentAnsatte(narmesteLederFnr: String): List<NærmesteLederFor> {
    return nonTransactionalExecuteQuery(
        """
            select * from naermeste_leder_kobling 
                where naermeste_leder_fnr = ?
            """, {
            text(narmesteLederFnr)
        }) {
        NærmesteLederFor(
            ansattFnr = getString("fnr"),
            virksomhetsnummer = getString("orgnummer"),
        )
    }
}
