package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.SakSortering.FRIST
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class SakMedOppgaverMedFristMedPaaminnelseTests : DescribeSpec({
    describe("Sak med oppgave med frist og påminnelse") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(
                    listOf(
                        BrukerModel.Tilgang.Altinn(
                            virksomhet = "1",
                            servicecode = "1",
                            serviceedition = "1",
                        )
                    )
                )
            }
        )
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

        val res =
            engine.querySakerJson(virksomhetsnummer = "1", limit = 10, sortering = FRIST)
                .getTypedContent<List<OffsetDateTime>>("$.saker.saker.*.oppgaver.*.paaminnelseTidspunkt")

        res.first().inOsloLocalDateTime() shouldBe påminnelsestidspunktLocalDateTime
    }
})

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
    paaminnelse: HendelseModel.Påminnelse,
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
        påminnelseOpprettet(
            oppgave = oppgaveOpprettet,
            opprettetTidpunkt = Instant.now(),
            frist = frist,
            tidspunkt = paaminnelse.tidspunkt,
        )
    }
}

