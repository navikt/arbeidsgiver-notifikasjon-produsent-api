package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class SakMedOppgaverMedFristMedPaminnelseTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
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

    var num_id : Int = 0

    suspend fun  opprettSakMedOppgaver(
        id : String = num_id++.toString(),
        frist1 : LocalDate? = null,
        frist2 : LocalDate? = null
    ) : String {
        val sakOpprettet = HendelseModel.SakOpprettet(
            hendelseId = uuid(id),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = uuid(id),
            grupperingsid = id,
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


        val oppgaveOpprettet = HendelseModel.OppgaveOpprettet(
            hendelseId = uuid(id),
            notifikasjonId = uuid(id+"1"),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = id,
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
            frist = frist1,
            påminnelse = null,
        )

        val oppgaveOpprettet2 = HendelseModel.OppgaveOpprettet(
            hendelseId = uuid(id),
            notifikasjonId = uuid(id+"2"),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = id,
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
            frist = frist2,
            påminnelse = null,
        )

        queryModel.oppdaterModellEtterHendelse(sakOpprettet)
        queryModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
        queryModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

        return id
    }

    describe("Sak som med oppgave") {
        val sak1 = opprettSakMedOppgaver(frist1 = LocalDate.parse("2023-01-10"), frist2 = LocalDate.parse("2023-01-10"))
        val sak2 = opprettSakMedOppgaver(frist1 = LocalDate.parse("2023-01-15"), frist2 = LocalDate.parse("2023-01-04"))
        val sak3 = opprettSakMedOppgaver(frist1 = LocalDate.parse("2023-01-05"), frist2 = LocalDate.parse("2023-01-25"))
        val sak4 = opprettSakMedOppgaver(frist1 = LocalDate.parse("2023-01-06"), frist2 = LocalDate.parse("2023-01-06"))

        val res1 = engine.hentSaker("1")
        println("Min test" + res1.content)
//        val res2 = engine.hentSaker(sak2)
//        val res3 = engine.hentSaker(sak3)
//        val res4 = engine.hentSaker(sak4)
        res1 shouldBe sak1
    }}
)

private fun TestApplicationEngine.hentSaker(id : String): TestApplicationResponse =
    brukerApi(
        """
                {
                    sak{
                        saker {
                            id                                              
                        }
                    }
                }
            """.trimIndent()
    )

