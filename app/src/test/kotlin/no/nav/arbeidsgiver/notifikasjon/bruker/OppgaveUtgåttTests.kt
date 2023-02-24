package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime

class OppgaveUtgåttTests : DescribeSpec({
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

    describe("oppgave utgått") {
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
            frist = null,
            påminnelse = null,
        )
        val oppgaveUtgått = HendelseModel.OppgaveUtgått(
            hendelseId = uuid("1"),
            notifikasjonId = uuid("1"),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            hardDelete = null,
            utgaattTidspunkt = OffsetDateTime.parse("2018-12-03T10:15:30+01:00"),
            nyLenke = null,
        )
        queryModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
        queryModel.oppdaterModellEtterHendelse(oppgaveUtgått)

        val oppgave = hentOppgave(engine)

        it("har tilstand utgått og utgått tidspunkt") {
            oppgave.tilstand shouldBe BrukerAPI.Notifikasjon.Oppgave.Tilstand.UTGAATT
            oppgave.utgaattTidspunkt shouldNotBe null
            oppgave.utgaattTidspunkt!!.toInstant() shouldBe oppgaveUtgått.utgaattTidspunkt.toInstant()
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
                            ...on Beskjed {
                                brukerKlikk { 
                                    __typename
                                    id
                                    klikketPaa 
                                }
                                lenke
                                tekst
                                merkelapp
                                opprettetTidspunkt
                                sorteringTidspunkt
                                id
                                virksomhet {
                                    virksomhetsnummer
                                    navn
                                }
                            }
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