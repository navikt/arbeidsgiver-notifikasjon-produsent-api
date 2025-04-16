package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.virksomhetsnummer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NærmesteLederTilgangsstyringTest {

    suspend fun BrukerRepository.beskjedOpprettet(mottaker: Mottaker) = beskjedOpprettet(
        virksomhetsnummer = mottaker.virksomhetsnummer,
        mottakere = listOf(mottaker),
    )

    @Test
    fun `Tilgangsstyring av nærmeste leder`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)

        val virksomhet1 = "1".repeat(9)
        val virksomhet2 = "2".repeat(9)
        val nærmesteLeder = "1".repeat(11)
        val annenNærmesteLeder = "2".repeat(11)
        val ansatt1 = "3".repeat(11)

        val mottaker1 = NærmesteLederMottaker(
            naermesteLederFnr = nærmesteLeder,
            ansattFnr = ansatt1,
            virksomhetsnummer = virksomhet1,
        )

        val mottaker2 = NærmesteLederMottaker(
            naermesteLederFnr = annenNærmesteLeder,
            ansattFnr = ansatt1,
            virksomhetsnummer = virksomhet1,
        )

        val mottaker3 = NærmesteLederMottaker(
            naermesteLederFnr = nærmesteLeder,
            ansattFnr = ansatt1,
            virksomhetsnummer = virksomhet2,
        )

        val beskjed1 = brukerRepository.beskjedOpprettet(mottaker = mottaker1)
        brukerRepository.beskjedOpprettet(mottaker = mottaker2)
        val beskjed3 = brukerRepository.beskjedOpprettet(mottaker = mottaker3)
        with(
            brukerRepository.hentNotifikasjoner(
                nærmesteLeder, AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                )
            )
        ) {
            assertTrue(isEmpty())
        }


        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("12"),
                fnr = mottaker1.ansattFnr,
                narmesteLederFnr = mottaker1.naermesteLederFnr,
                orgnummer = mottaker1.virksomhetsnummer,
                aktivTom = null,
            )
        )

        // får notifikasjon om nåværende ansatt
        with(
            brukerRepository.hentNotifikasjoner(
                nærmesteLeder, AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                )
            )
        ) {
            assertEquals(1, size)
            val beskjed = first() as BrukerModel.Beskjed
            assertEquals(beskjed.id, beskjed1.notifikasjonId)
        }

        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("13"),
                fnr = mottaker1.ansattFnr,
                narmesteLederFnr = mottaker1.naermesteLederFnr,
                orgnummer = virksomhet2,
                aktivTom = null,
            )
        )

        // får ikke notifikasjon om ansatte i andre virksomheter,
        // selv om du er nærmeste leder for den personen i denne virksomheten
        with(
            brukerRepository.hentNotifikasjoner(
                nærmesteLeder,
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                ),
                //ansatte(mottaker1.copy(virksomhetsnummer = virksomhet2))
            )
        ) {
            assertEquals(2, size)
            assertEquals(
                listOf(beskjed1.notifikasjonId, beskjed3.notifikasjonId).sorted(),
                map { it.id }.sorted()
            )
        }
    }
}

