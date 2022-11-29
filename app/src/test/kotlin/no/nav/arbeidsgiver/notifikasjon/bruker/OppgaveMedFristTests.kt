package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime

class OppgaveMedFristTests : DescribeSpec({
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

    describe("oppgave med frist") {
        val oppgaveOpprettet = HendelseModel.OppgaveOpprettet(
            hendelseId = uuid("0"),
            notifikasjonId = uuid("1"),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = "1",
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
            frist = LocalDate.parse("2007-12-03"),
            påminnelse = null,
        )
        queryModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

        val oppgave = hentOppgave(engine)

        it("har frist") {
            oppgave.frist shouldBe oppgaveOpprettet.frist
        }

    }
})

private fun hentOppgave(engine: TestApplicationEngine): BrukerAPI.Notifikasjon.Oppgave =
    engine.brukerApi(
        """
                {
                    notifikasjoner{
                        notifikasjoner {
                            __typename
                            ...on Oppgave {
                                brukerKlikk { 
                                    __typename
                                    id
                                    klikketPaa 
                                }
                                lenke
                                tilstand
                                tekst
                                merkelapp
                                opprettetTidspunkt
                                sorteringTidspunkt
                                utgaattTidspunkt
                                id
                                frist
                                virksomhet {
                                    virksomhetsnummer
                                    navn
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
    ).getTypedContent("notifikasjoner/notifikasjoner/0")