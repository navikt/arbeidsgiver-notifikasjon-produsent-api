package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.NY
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.SakSortering.FRIST
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class SakerMedOppgaveTilstandTests : DescribeSpec({

    describe("Sak med oppgave med frist og påminnelse") {
        val (repo, engine) = setupRepoOgEngine()
        val sak = repo.opprettSak("1")
        repo.opprettOppgave(sak, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }
        repo.opprettOppgave(sak, LocalDate.parse("2023-05-15"))
        repo.opprettOppgave(sak, LocalDate.parse("2023-05-15"))
        repo.opprettOppgave(sak, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtgått(it) }

        val sak2 = repo.opprettSak("2")
        repo.opprettOppgave(sak2, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }
        repo.opprettOppgave(sak2, LocalDate.parse("2023-05-15"))

        val sak3 = repo.opprettSak("3")
        repo.opprettOppgave(sak3, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }

        val res = engine.querySakerJson(
            virksomhetsnummer = "1",
            limit = 10,
            sortering = FRIST,
        )

        it("Teller kun saken en gang for hver tilstand") {
            res.getTypedContent<List<Any>>("$.saker.oppgaveTilstandInfo") shouldContainExactlyInAnyOrder listOf(
                mapOf(
                    "tilstand" to "NY",
                    "antall" to 2
                ),
                mapOf(
                    "tilstand" to "UTGAATT",
                    "antall" to 1
                ),
                mapOf(
                    "tilstand" to "UTFOERT",
                    "antall" to 3
                )
            )
        }

        it("totaltAntallSaker teller saker og ikke oppgaver") {
            res.getTypedContent<Int>("$.saker.totaltAntallSaker") shouldBe 3
        }
    }

    describe("Sak med oppgave med frist med filter") {
        val (repo, engine) = setupRepoOgEngine()
        val sak1 = repo.opprettSak("1")
        repo.opprettOppgave(sak1, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }
        repo.opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
        repo.opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
        repo.opprettOppgave(sak1, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtgått(it) }

        val sak2 = repo.opprettSak("2")
        repo.opprettOppgave(sak2, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }
        repo.opprettOppgave(sak2, LocalDate.parse("2023-05-15")).also { repo.oppgaveTilstandUtført(it) }
        repo.opprettOppgave(sak2, LocalDate.parse("2023-05-15")).also { repo.oppgaveTilstandUtgått(it) }

        repo.opprettSak("3")

        val res =
            engine.querySakerJson(
                virksomhetsnummer = "1",
                limit = 10,
                sortering = FRIST,
                oppgaveTilstand = listOf(NY)
            ).getTypedContent<List<UUID>>("$.saker.saker.*.id")


        res shouldBe listOf(sak1.sakId)
    }

    describe("Saker med og uten oppgaver") {
        val (repo, engine) = setupRepoOgEngine()
        val sak1 = repo.opprettSak("1")
        val sak2 = repo.opprettSak("2")
        repo.opprettOppgave(sak2, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }

        val res = engine.querySakerJson(
            virksomhetsnummer = "1",
            limit = 10,
            sortering = FRIST,
        ).getTypedContent<List<UUID>>("$.saker.saker.*.id")

        it("skal returnere saker med og uten oppgaver") {
            res shouldContainExactlyInAnyOrder listOf(sak1.sakId, sak2.sakId)
        }
    }
})

private suspend fun BrukerRepository.opprettOppgave(
    sak: HendelseModel.SakOpprettet,
    frist: LocalDate?,
) = oppgaveOpprettet(
    virksomhetsnummer = "1",
    produsentId = "1",
    kildeAppNavn = "1",
    grupperingsid = sak.grupperingsid,
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
    påminnelse = null,
)

private suspend fun BrukerRepository.opprettStatus(sak: HendelseModel.SakOpprettet) = nyStatusSak(
    sak = sak,
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

private suspend fun BrukerRepository.opprettSak(
    id: String,
): HendelseModel.SakOpprettet {
    val uuid = uuid(id)
    val sakOpprettet = sakOpprettet(
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
    )
    opprettStatus(sakOpprettet)

    return sakOpprettet
}

private suspend fun BrukerRepository.oppgaveTilstandUtført(oppgaveOpprettet: HendelseModel.OppgaveOpprettet) =
    oppgaveUtført(
        oppgaveOpprettet,
        hardDelete = null,
        nyLenke = null,
        utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
    )

private suspend fun BrukerRepository.oppgaveTilstandUtgått(oppgaveOpprettet: HendelseModel.OppgaveOpprettet) =
    oppgaveUtgått(
        oppgave = oppgaveOpprettet,
        hardDelete = null,
        utgaattTidspunkt = OffsetDateTime.now(),
        nyLenke = null,
    )

private fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
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
    return Pair(brukerRepository, engine)
}


