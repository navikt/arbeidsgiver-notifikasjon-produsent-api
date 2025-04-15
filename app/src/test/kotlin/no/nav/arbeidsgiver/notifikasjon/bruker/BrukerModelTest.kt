package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BrukerModelTest {

    val uuid = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
    val mottaker = NærmesteLederMottaker(
        naermesteLederFnr = "314",
        ansattFnr = "33314",
        virksomhetsnummer = "1337"
    )
    val event = BeskjedOpprettet(
        merkelapp = "foo",
        eksternId = "42",
        mottakere = listOf(mottaker),
        hendelseId = uuid,
        notifikasjonId = uuid,
        tekst = "teste",
        grupperingsid = "gr1",
        lenke = "foo.no/bar",
        opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
        virksomhetsnummer = mottaker.virksomhetsnummer,
        kildeAppNavn = "",
        produsentId = "",
        eksterneVarsler = listOf(),
        hardDelete = null,
        sakId = null,
    )


    @Test
    fun `Beskjed opprettet i BrukerModel`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("4321"),
                fnr = mottaker.ansattFnr,
                narmesteLederFnr = mottaker.naermesteLederFnr,
                orgnummer = mottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )
        brukerRepository.oppdaterModellEtterHendelse(event)

        val notifikasjoner = brukerRepository.hentNotifikasjoner(
            mottaker.naermesteLederFnr,
            AltinnTilganger(
                harFeil = false,
                tilganger = emptyList()
            ),
        )

        assertEquals(
            listOf(
                BrukerModel.Beskjed(
                    merkelapp = "foo",
                    eksternId = "42",
                    virksomhetsnummer = mottaker.virksomhetsnummer,
                    tekst = "teste",
                    grupperingsid = "gr1",
                    lenke = "foo.no/bar",
                    opprettetTidspunkt = event.opprettetTidspunkt,
                    id = uuid,
                    klikketPaa = false
                )
            ),
            notifikasjoner
        )
    }

    @Test
    fun `notifikasjon mottas flere ganger (fra kafka feks)`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("4321"),
                fnr = mottaker.ansattFnr,
                narmesteLederFnr = mottaker.naermesteLederFnr,
                orgnummer = mottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )

        brukerRepository.oppdaterModellEtterHendelse(event)
        brukerRepository.oppdaterModellEtterHendelse(event)

        val notifikasjoner = brukerRepository.hentNotifikasjoner(
            mottaker.naermesteLederFnr,
            AltinnTilganger(
                harFeil = false,
                tilganger = emptyList()
            ),
        )
        assertEquals(
            listOf(
                BrukerModel.Beskjed(
                    merkelapp = "foo",
                    eksternId = "42",
                    virksomhetsnummer = mottaker.virksomhetsnummer,
                    tekst = "teste",
                    grupperingsid = "gr1",
                    lenke = "foo.no/bar",
                    opprettetTidspunkt = event.opprettetTidspunkt,
                    id = uuid,
                    klikketPaa = false
                )
            ),
            notifikasjoner
        )
    }
}
