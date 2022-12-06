package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class OppgaveMedPåminnelseTests : DescribeSpec({
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

    describe("oppgave med påminnelse blir bumpet og klikk state clearet") {
        val tidspunkt = OffsetDateTime.parse("2020-12-03T10:15:30+01:00")
        val oppgave0 = oppgaveOpprettet(uuid("0"), tidspunkt).also { queryModel.oppdaterModellEtterHendelse(it) }
        oppgaveOpprettet(uuid("1"), tidspunkt.plusMinutes(10)).also { queryModel.oppdaterModellEtterHendelse(it) }
        oppgaveOpprettet(uuid("2"), tidspunkt.plusMinutes(20)).also { queryModel.oppdaterModellEtterHendelse(it) }
        brukerKlikket(uuid("0")).also { queryModel.oppdaterModellEtterHendelse(it) }

        val response1 = engine.hentOppgaver()
        it("listen er sortert og entry id=1 er klikket på") {
            val oppgaver = response1.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                uuid("2"),
                uuid("1"),
                uuid("0"),
            )
            val harPåminnelse = response1.getTypedContent<List<OffsetDateTime?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                .map { it != null }
            harPåminnelse shouldBe listOf(false, false, false)

            val klikketPaa = response1.getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
            klikketPaa shouldBe listOf(
                false,
                false,
                true,
            )
        }

        påminnelseOpprettet(oppgave0, tidspunkt.plusMinutes(15).inOsloLocalDateTime()).also { queryModel.oppdaterModellEtterHendelse(it) }

        val response2 = engine.hentOppgaver()
        it("listen er sortert på rekkefølge og entry 1 er klikket på") {
            val oppgaver = response2.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                uuid("2"),
                uuid("0"),
                uuid("1"),
            )
            val klikketPaa = response2.getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
            klikketPaa shouldBe listOf(
                false,
                false,
                false,
            )
            val harPåminnelse = response2.getTypedContent<List<Any?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                .map { it != null }
            harPåminnelse shouldBe listOf(false, true, false)
        }
    }
})

private fun påminnelseOpprettet(oppgave: HendelseModel.OppgaveOpprettet, konkretPåminnelseTidspunkt: LocalDateTime) =
    HendelseModel.PåminnelseOpprettet(
        virksomhetsnummer = "1",
        hendelseId = UUID.randomUUID(),
        produsentId = "1",
        kildeAppNavn = "1",
        notifikasjonId = oppgave.notifikasjonId,
        opprettetTidpunkt = Instant.now(),
        oppgaveOpprettetTidspunkt = oppgave.opprettetTidspunkt.toInstant(),
        frist = oppgave.frist,
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
            konkret = konkretPåminnelseTidspunkt,
            opprettetTidspunkt = oppgave.opprettetTidspunkt,
            frist = oppgave.frist
        ),
        eksterneVarsler = listOf(),
    )

private fun brukerKlikket(notifikasjonId: UUID) = HendelseModel.BrukerKlikket(
    virksomhetsnummer = "1",
    notifikasjonId = notifikasjonId,
    hendelseId = UUID.randomUUID(),
    kildeAppNavn = NaisEnvironment.clientId,
    fnr = "0".repeat(11),
)

private fun oppgaveOpprettet(id: UUID, opprettetTidspunkt: OffsetDateTime) = HendelseModel.OppgaveOpprettet(
    hendelseId = id,
    notifikasjonId = id,
    virksomhetsnummer = "1",
    produsentId = "1",
    kildeAppNavn = "1",
    grupperingsid = "1",
    eksternId = "1",
    eksterneVarsler = listOf(),
    opprettetTidspunkt = opprettetTidspunkt,
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

private fun TestApplicationEngine.hentOppgaver(): TestApplicationResponse =
    brukerApi(
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
                                sorteringTidspunkt
                                paaminnelseTidspunkt
                                id
                            }
                        }
                    }
                }
            """.trimIndent()
    )