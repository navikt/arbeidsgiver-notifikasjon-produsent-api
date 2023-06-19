package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime


class NyLenkeOppgaveTests: DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    describe("Oppgave med uendret lenke") {
        queryModel.oppdaterModellEtterHendelse(oppgaveOpprettet("https://opprettelse-lenke"))
        it("Starter med lenken fra hendelse") {
            queryModel.hentLenke() shouldBe "https://opprettelse-lenke"
        }

        queryModel.oppdaterModellEtterHendelse(oppgaveUtført(null))
        it("Fortsatt med lenke fra opprettelse") {
            queryModel.hentLenke() shouldBe "https://opprettelse-lenke"
        }
    }

    describe("Oppgave med endret lenke") {
        queryModel.oppdaterModellEtterHendelse(oppgaveOpprettet("https://opprettelse-lenke"))
        it("Starter med lenken fra hendelse") {
            queryModel.hentLenke() shouldBe "https://opprettelse-lenke"
        }

        queryModel.oppdaterModellEtterHendelse(oppgaveUtført("https://utført-lenke"))
        it("Ny lenke fra utført") {
            queryModel.hentLenke() shouldBe "https://utført-lenke"
        }
    }
})

private val mottaker = HendelseModel.AltinnMottaker(serviceCode = "1", serviceEdition = "1", virksomhetsnummer = "1".repeat(9))

private fun oppgaveOpprettet(lenke: String) = HendelseModel.OppgaveOpprettet(
    virksomhetsnummer = mottaker.virksomhetsnummer,
    notifikasjonId = uuid("0"),
    hendelseId = uuid("0"),
    produsentId = "test-produsent",
    kildeAppNavn = "test",
    merkelapp = "tag",
    eksternId = "e1",
    mottakere = listOf(mottaker),
    tekst = "some tekst",
    grupperingsid = null,
    lenke = lenke,
    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01:01Z"),
    eksterneVarsler = listOf(),
    hardDelete = null,
    frist = null,
    påminnelse = null,
)

private fun oppgaveUtført(nyLenke: String?) = HendelseModel.OppgaveUtført(
    virksomhetsnummer = mottaker.virksomhetsnummer,
    notifikasjonId = uuid("0"),
    hendelseId = uuid("1"),
    produsentId = "test-produsent",
    kildeAppNavn = "test",
    hardDelete = null,
    nyLenke = nyLenke,
    utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
)

suspend fun BrukerRepository.hentLenke() =
    hentNotifikasjoner(
        fnr = "",
        tilganger = BrukerModel.Tilganger(tjenestetilganger = listOf(BrukerModel.Tilgang.Altinn(
            servicecode = mottaker.serviceCode,
            serviceedition = mottaker.serviceEdition,
            virksomhet = mottaker.virksomhetsnummer,
        )))
    )
        .filterIsInstance<BrukerModel.Oppgave>()
        .first()
        .lenke

