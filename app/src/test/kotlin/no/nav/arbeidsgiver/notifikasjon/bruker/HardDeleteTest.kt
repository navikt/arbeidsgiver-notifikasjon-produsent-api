package no.nav.arbeidsgiver.notifikasjon.bruker

import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardDeleteTest {
    @Test
    fun `HardDelete av notifikasjon`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val uuid1 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val uuid2 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130004")

        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )

        suspend fun opprettBeskjed(id: UUID) = brukerRepository.beskjedOpprettet(
            mottakere = listOf(mottaker),
            notifikasjonId = id,
            virksomhetsnummer = mottaker.virksomhetsnummer,
        )

        val hardDeleteEvent = HardDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = uuid1,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            deletedAt = OffsetDateTime.MAX,
            kildeAppNavn = "",
            produsentId = "",
            grupperingsid = null,
            merkelapp = null,
        )


        opprettBeskjed(uuid1)
        opprettBeskjed(uuid2)
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("43"),
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
                    tilganger = emptyList()
                ),
            )
                .map { it.id }
                .sorted()

        assertEquals(listOf(uuid1, uuid2).sorted(), notifikasjoner)

        brukerRepository.oppdaterModellEtterHendelse(hardDeleteEvent)
        val notifikasjonerEtterSletting = brukerRepository.hentNotifikasjoner(
            mottaker.naermesteLederFnr,
            AltinnTilganger(
                harFeil = false,
                tilganger = emptyList()
            ),
        )
            .map { it.id }

        assertEquals(listOf(uuid2), notifikasjonerEtterSletting)
    }

    @Test
    fun `HardDelete av sak`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val uuid1 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val uuid2 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130004")

        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )

        suspend fun opprettEvent(id: UUID) = brukerRepository.sakOpprettet(
            merkelapp = "foo",
            mottakere = listOf(mottaker),
            sakId = id,
            tittel = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            virksomhetsnummer = mottaker.virksomhetsnummer,
        )

        suspend fun opprettStatusEvent(sak: HendelseModel.SakOpprettet) = brukerRepository.nyStatusSak(
            sak,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            idempotensKey = IdempotenceKey.initial(),
            status = HendelseModel.SakStatus.MOTTATT,
        )

        val hardDeleteEvent = HardDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = uuid1,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            deletedAt = OffsetDateTime.MAX,
            kildeAppNavn = "",
            produsentId = "",
            grupperingsid = "gr1",
            merkelapp = "foo",
        )


        val sak1 = opprettEvent(uuid1)
        brukerRepository.oppdaterModellEtterHendelse(opprettStatusEvent(sak1))
        val beskjed = brukerRepository.beskjedOpprettet(
            merkelapp = sak1.merkelapp,
            grupperingsid = sak1.grupperingsid,
            virksomhetsnummer = sak1.virksomhetsnummer,
            mottakere = sak1.mottakere,
        )
        val sak2 = opprettEvent(uuid2)
        brukerRepository.oppdaterModellEtterHendelse(opprettStatusEvent(sak2))
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("43"),
                fnr = mottaker.ansattFnr,
                narmesteLederFnr = mottaker.naermesteLederFnr,
                orgnummer = mottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )

        val saker =
            brukerRepository.hentSaker(
                fnr = mottaker.naermesteLederFnr,
                virksomhetsnummer = listOf(mottaker.virksomhetsnummer),
                altinnTilganger = AltinnTilganger(
                    harFeil = false,
                    tilganger = emptyList()
                ),
                tekstsoek = null,
                sakstyper = null,
                offset = 0,
                limit = Integer.MAX_VALUE,
                oppgaveTilstand = null,
                oppgaveFilter = null,
                sortering = BrukerAPI.SakSortering.NYESTE
            ).saker
                .map { it.sakId }
                .sorted()

        assertEquals(listOf(uuid1, uuid2).sorted(), saker)

        assertEquals(
            listOf(beskjed.aggregateId),
            brukerRepository.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                AltinnTilganger(
                    harFeil = false,
                    tilganger = emptyList()
                ),
            ).map { it.id }
        )

        brukerRepository.oppdaterModellEtterHendelse(hardDeleteEvent)
        val sakerEtterSletting = brukerRepository.hentSaker(
            fnr = mottaker.naermesteLederFnr,
            virksomhetsnummer = listOf(mottaker.virksomhetsnummer),
            altinnTilganger = AltinnTilganger(
                harFeil = false,
                tilganger = emptyList()
            ),
            tekstsoek = null,
            sakstyper = null,
            offset = 0,
            limit = Integer.MAX_VALUE,
            oppgaveTilstand = null,
            oppgaveFilter = null,
            sortering = BrukerAPI.SakSortering.NYESTE
        ).saker.map { it.sakId }

        assertEquals(
            listOf(uuid2),
            sakerEtterSletting
        )
        assertTrue(
            brukerRepository.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                AltinnTilganger(
                    harFeil = false,
                    tilganger = emptyList()
                ),
            ).isEmpty()
        )
    }
}

