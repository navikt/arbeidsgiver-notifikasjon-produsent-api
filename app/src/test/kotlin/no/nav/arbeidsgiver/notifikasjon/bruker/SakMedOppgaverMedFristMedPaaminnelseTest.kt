package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SakMedOppgaverMedFristMedPaaminnelseTest {

    @Test
    fun `Sak med oppgave med frist og påminnelse`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("1", "1:1"),
                    ),
                )
            }
        ) {
            val påminnelsestidspunktLocalDateTime = LocalDateTime.parse("2023-01-02T12:15:00")
            val sak1 = brukerRepository.opprettSak("1")
            brukerRepository.opprettOppgave(
                sak1,
                LocalDate.parse("2023-01-15"),
                HendelseModel.Påminnelse(
                    HendelseModel.PåminnelseTidspunkt.Konkret(
                        påminnelsestidspunktLocalDateTime,
                        påminnelsestidspunktLocalDateTime.inOsloAsInstant()
                    ),
                    emptyList()
                )
            )

            with(
                client.querySakerJson(
                    virksomhetsnummer = "1",
                    limit = 10
                ).getTypedContent<List<OffsetDateTime>>("$.saker.saker.*.oppgaver.*.paaminnelseTidspunkt")
            ) {
                assertEquals(påminnelsestidspunktLocalDateTime, first().inOsloLocalDateTime())
            }
        }
    }
}

private suspend fun BrukerRepository.opprettSak(
    id: String,
) = uuid(id).also { uuid ->
    sakOpprettet(
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid,
        grupperingsid = uuid.toString(),
        merkelapp = "tag",
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        tittel = "tjohei",
        lenke = "#foo",
        oppgittTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
        mottattTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
        hardDelete = null,
    ).let { sakOpprettet ->
        nyStatusSak(
            sakOpprettet,
            hendelseId = UUID.randomUUID(),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            status = HendelseModel.SakStatus.MOTTATT,
            overstyrStatustekstMed = null,
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.now(),
            idempotensKey = IdempotenceKey.initial(),
            hardDelete = null,
            nyLenkeTilSak = null,
        )
    }
}.toString()

private suspend fun BrukerRepository.opprettOppgave(
    grupperingsid: String,
    frist: LocalDate?,
    paaminnelse: HendelseModel.Påminnelse?,
) {
    oppgaveOpprettet(
        notifikasjonId = UUID.randomUUID(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        grupperingsid = grupperingsid,
        eksternId = "1",
        eksterneVarsler = listOf(),
        opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
        merkelapp = "tag",
        tekst = "tjohei",
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        lenke = "#foo",
        hardDelete = null,
        frist = frist,
        påminnelse = paaminnelse,
    ).let { oppgaveOpprettet ->
        if (oppgaveOpprettet.påminnelse !== null)
            påminnelseOpprettet(
                oppgave = oppgaveOpprettet,
                opprettetTidpunkt = Instant.now(),
                frist = frist,
                tidspunkt = paaminnelse!!.tidspunkt,
            )
    }
}

