package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.*

class SoftDeleteTest {
    val virksomhetsnummer = "1"

    val mottaker = NærmesteLederMottaker(
        naermesteLederFnr = "314",
        ansattFnr = "33314",
        virksomhetsnummer = virksomhetsnummer
    )


    @Test
    fun `SoftDelete av notifikasjon`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)

        val beskjed1 = brukerRepository.beskjedOpprettet(
            mottakere = listOf(mottaker),
            virksomhetsnummer = mottaker.virksomhetsnummer,
        )
        val beskjed2 = brukerRepository.beskjedOpprettet(
            mottakere = listOf(mottaker),
            virksomhetsnummer = mottaker.virksomhetsnummer,
        )

        // oppretter to beskjeder i databasen
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("432"),
                fnr = mottaker.ansattFnr,
                narmesteLederFnr = mottaker.naermesteLederFnr,
                orgnummer = mottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )

        val notifikasjoner =
            brukerRepository.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                ),
            )
                .map { it.id }
                .sorted()

        assertEquals(
            listOf(beskjed1.notifikasjonId, beskjed2.notifikasjonId).sorted(),
            notifikasjoner
        )

        // sletter kun ønsket beskjed
        brukerRepository.oppdaterModellEtterHendelse(
            SoftDelete(
                hendelseId = UUID.randomUUID(),
                aggregateId = beskjed1.notifikasjonId,
                virksomhetsnummer = mottaker.virksomhetsnummer,
                deletedAt = OffsetDateTime.MAX,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                merkelapp = null,
            )
        )
        val notifikasjonerEtterSletting = brukerRepository.hentNotifikasjoner(
            mottaker.naermesteLederFnr,
            AltinnTilganger(
                harFeil = false,
                tilganger = listOf()
            ),
        ).map { it.id }

        assertEquals(
            listOf(beskjed2.notifikasjonId),
            notifikasjonerEtterSletting
        )
    }

    @Test
    fun `SoftDelete cascader på sak`() = withTestDatabase(Bruker.databaseConfig) { database ->
        // sletter alle notifikasjoner knyttet til en sak
        val brukerRepository = BrukerRepositoryImpl(database)

        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("432"),
                fnr = mottaker.ansattFnr,
                narmesteLederFnr = mottaker.naermesteLederFnr,
                orgnummer = mottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )

        val sak1 = brukerRepository.sakOpprettet(
            grupperingsid = "1",
            mottakere = listOf(mottaker),
        )
        val sak2 = brukerRepository.sakOpprettet(
            grupperingsid = "2",
            mottakere = listOf(mottaker),
        )
        val oppgave1 = brukerRepository.oppgaveOpprettet(
            sak = sak1,
        )
        val oppgave2 = brukerRepository.oppgaveOpprettet(
            sak = sak2,
        )
        val beskjed1 = brukerRepository.beskjedOpprettet(
            sak = sak1,
        )
        val beskjed2 = brukerRepository.beskjedOpprettet(
            sak = sak2,
        )
        val kalenderavtale1 = brukerRepository.kalenderavtaleOpprettet(
            sak = sak1,
        )
        val kalenderavtale2 = brukerRepository.kalenderavtaleOpprettet(
            sak = sak2,
        )

        brukerRepository.softDelete(
            sak = sak1
        )

        val notifikasjoner = brukerRepository.hentNotifikasjoner(
            mottaker.naermesteLederFnr,
            AltinnTilganger(
                harFeil = false,
                tilganger = listOf()
            ),
        ).map { it.id }


        val sak1EtterSletting = brukerRepository.hentSakById(
            id = sak1.sakId,
            fnr = mottaker.naermesteLederFnr,
            altinnTilganger = AltinnTilganger(
                harFeil = false,
                tilganger = listOf()
            ),
        )
        val sak2EtterSletting = brukerRepository.hentSakById(
            id = sak2.sakId,
            fnr = mottaker.naermesteLederFnr,
            altinnTilganger = AltinnTilganger(
                harFeil = false,
                tilganger = listOf()
            ),
        )

        assertNull(sak1EtterSletting)
        assertNotNull(sak2EtterSletting)
        assertFalse(notifikasjoner.contains(oppgave1.notifikasjonId))
        assertTrue(notifikasjoner.contains(oppgave2.notifikasjonId))
        assertFalse(notifikasjoner.contains(beskjed1.notifikasjonId))
        assertTrue(notifikasjoner.contains(beskjed2.notifikasjonId))
        assertFalse(notifikasjoner.contains(kalenderavtale1.notifikasjonId))
        assertTrue(notifikasjoner.contains(kalenderavtale2.notifikasjonId))
    }
}

