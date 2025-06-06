package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SakerMedOppgaveTilstandTest {

    @Test
    fun `Sak med oppgave med frist og påminnelse`() = withTestDatabase(Bruker.databaseConfig, "SakerMedOppgaveTilstandTest") { database ->
        val repo = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = repo,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("1", "1:1"),
                    ),
                )
            }
        ) {
            val sak1 = repo.opprettSak("1")
            val date = LocalDate.parse("2023-01-15")

            repo.opprettOppgave(sak1, date).also { repo.oppgaveTilstandUtført(it) }
            repo.opprettOppgave(sak1, LocalDate.parse("2023-05-22"))
                .also { repo.påminnelseOpprettet(it, date.plusDays(7).atTime(LocalTime.MAX)) }
            repo.opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
                .also { repo.påminnelseOpprettet(it, date.plusDays(7).atTime(LocalTime.MAX)) }
            repo.opprettOppgave(sak1, date).also { repo.oppgaveTilstandUtgått(it) }
                .also { repo.påminnelseOpprettet(it, date.minusDays(7).atTime(LocalTime.MAX)) }


            val sak2 = repo.opprettSak("2")
            repo.opprettOppgave(sak2, date).also { repo.oppgaveTilstandUtført(it) }
                .also { repo.påminnelseOpprettet(it, date.minusDays(7).atTime(LocalTime.MAX)) }
            repo.opprettOppgave(sak2, LocalDate.parse("2023-05-15"))


            val sak3 = repo.opprettSak("3")
            repo.opprettOppgave(sak3, date).also { repo.oppgaveTilstandUtført(it) }

            repo.opprettSak("4").also { sak ->
                repo.kalenderavtaleOpprettet(
                    sakId = sak.sakId,
                    tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
                    merkelapp = sak.merkelapp,
                    grupperingsid = sak.grupperingsid,
                )
            }

            repo.opprettSak("5").also { sak ->
                repo.kalenderavtaleOpprettet(
                    sakId = sak.sakId,
                    tilstand = HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_VIL_AVLYSE,
                    merkelapp = sak.merkelapp,
                    grupperingsid = sak.grupperingsid,
                )
            }

            repo.opprettSak("6").also { sak ->
                repo.opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
                repo.kalenderavtaleOpprettet(
                    sakId = sak.sakId,
                    tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
                    merkelapp = sak.merkelapp,
                    grupperingsid = sak.grupperingsid,
                )
            }

            val res = client.querySakerJson(
                virksomhetsnummer = "1",
                limit = 10,
            )

            // Teller kun saken en gang for hver tilstand
            assertEquals(
                listOf(
                    mapOf(
                        "tilstand" to "NY",
                        "antall" to 4
                    ),
                    mapOf(
                        "tilstand" to "UTFOERT",
                        "antall" to 3
                    ),
                    mapOf(
                        "tilstand" to "UTGAATT",
                        "antall" to 1
                    ),
                ),
                res.getTypedContent<List<Any>>("$.saker.oppgaveTilstandInfo")
            )


            // Teller kun saken en gang for hver filterInfo
            assertEquals(
                listOf(
                    mapOf(
                        "filterType" to BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY.name,
                        "antall" to 4
                    ),
                    mapOf(
                        "filterType" to BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST.name,
                        "antall" to 1
                    ),
                    mapOf(
                        "filterType" to BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_UTGAATT.name,
                        "antall" to 1
                    ),
                    mapOf(
                        "filterType" to BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_UTFOERT.name,
                        "antall" to 3
                    ),
                ),
                res.getTypedContent<List<Any>>("$.saker.oppgaveFilterInfo")
            )

            // totaltAntallSaker teller saker og ikke oppgaver
            assertEquals(6, res.getTypedContent<Int>("$.saker.totaltAntallSaker"))
        }
    }

    @Test
    fun `Sak med oppgave med frist med filter`() = withTestDatabase(Bruker.databaseConfig, "SakerMedOppgaveTilstandTest") { database ->
        val repo = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = repo,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("1", "1:1"),
                    ),
                )
            }
        ) {
            val sak1 = repo.opprettSak("1")
            repo.opprettOppgave(sak1, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }
            repo.opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
            repo.opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
            repo.opprettOppgave(sak1, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtgått(it) }

            repo.opprettSak("2").also { sak ->
                repo.opprettOppgave(sak, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }
                repo.opprettOppgave(sak, LocalDate.parse("2023-05-15")).also { repo.oppgaveTilstandUtført(it) }
                repo.opprettOppgave(sak, LocalDate.parse("2023-05-15")).also { repo.oppgaveTilstandUtgått(it) }
                for (tilstand in listOf(
                    HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_VIL_AVLYSE,
                    HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
                    HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_HAR_GODTATT,
                    HendelseModel.KalenderavtaleTilstand.AVLYST,
                    HendelseModel.KalenderavtaleTilstand.AVHOLDT,
                )) {
                    repo.kalenderavtaleOpprettet(
                        sakId = sak.sakId,
                        tilstand = tilstand,
                        merkelapp = sak.merkelapp,
                        grupperingsid = sak.grupperingsid,
                    )
                }
            }

            val sak3 = repo.opprettSak("3").also { sak ->
                repo.kalenderavtaleOpprettet(
                    sakId = sak.sakId,
                    tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
                    merkelapp = sak.merkelapp,
                    grupperingsid = sak.grupperingsid,
                )
            }

            val res =
                client.querySakerJson(
                    virksomhetsnummer = "1",
                    limit = 10,
                    oppgaveTilstand = listOf(BrukerAPI.Notifikasjon.Oppgave.Tilstand.NY)
                ).getTypedContent<List<UUID>>("$.saker.saker.*.id")


            assertEquals(
                listOf(sak1.sakId, sak3.sakId).sorted(),
                res.sorted(),
            )
        }
    }

    @Test
    fun `Saker med og uten oppgaver`() = withTestDatabase(Bruker.databaseConfig, "SakerMedOppgaveTilstandTest") { database ->
        val repo = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = repo,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("1", "1:1"),
                    ),
                )
            }
        ) {
            val sak1 = repo.opprettSak("1")
            val sak2 = repo.opprettSak("2")
            repo.opprettOppgave(sak2, LocalDate.parse("2023-01-15")).also { repo.oppgaveTilstandUtført(it) }

            val res = client.querySakerJson(
                virksomhetsnummer = "1",
                limit = 10,
            ).getTypedContent<List<UUID>>("$.saker.saker.*.id")

            // skal returnere saker med og uten oppgaver
            assertEquals(
                listOf(sak1.sakId, sak2.sakId).sorted(),
                res.sorted(),
            )
        }
    }

    @Test
    fun `Saker med merkelapp filter og oppgaveFilter`() = withTestDatabase(Bruker.databaseConfig, "SakerMedOppgaveTilstandTest") { database ->
        val repo = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = repo,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(
                        AltinnTilgang("1", "1:1"),
                    ),
                )
            }
        ) {
            val sak1 = repo.opprettSak("1", merkelapp = "merkelapp1")
            val sak2 = repo.opprettSak("2", merkelapp = "merkelapp1")
            val sak3 = repo.opprettSak("3", merkelapp = "merkelapp2")
            repo.opprettOppgave(sak1, null)
                .also { repo.påminnelseOpprettet(it, LocalDate.parse("2023-01-15").plusDays(7).atTime(LocalTime.MAX)) }
            repo.opprettOppgave(sak1, null)
            repo.opprettOppgave(sak2, null)
            repo.opprettOppgave(sak2, null)
            repo.opprettOppgave(sak3, null)
                .also { repo.påminnelseOpprettet(it, LocalDate.parse("2023-01-15").plusDays(7).atTime(LocalTime.MAX)) }
            repo.opprettOppgave(sak3, null).also { repo.oppgaveTilstandUtført(it) }

            // skal returnere saker med merkelapp1, utløst påminnelse og riktig antall
            with(
                client.querySakerJson(
                    virksomhetsnummer = "1",
                    limit = 10,
                    sakstyper = listOf("merkelapp1"),
                    oppgaveFilter = listOf(BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST)
                )
            ) {
                assertEquals(
                    listOf(
                        BrukerAPI.OppgaveFilterInfo(
                            BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST,
                            1
                        ),
                        BrukerAPI.OppgaveFilterInfo(BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY, 2),
                    ),
                    getTypedContent<List<BrukerAPI.OppgaveFilterInfo>>("$.saker.oppgaveFilterInfo")
                )
            }


            // skal returnere saker med merkelapp 1 og merkelapp2, utløst påminnelse og riktig antall
            with(
                client.querySakerJson(
                    virksomhetsnummer = "1",
                    limit = 10,
                    oppgaveFilter = listOf(BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST),
                    sakstyper = listOf("merkelapp1", "merkelapp2")
                )
            ) {
                assertEquals(
                    listOf(
                        BrukerAPI.OppgaveFilterInfo(
                            BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST,
                            2
                        ),
                        BrukerAPI.OppgaveFilterInfo(BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_UTFOERT, 1),
                        BrukerAPI.OppgaveFilterInfo(BrukerAPI.OppgaveFilterInfo.OppgaveFilterType.TILSTAND_NY, 3),
                    ),
                    getTypedContent<List<BrukerAPI.OppgaveFilterInfo>>("$.saker.oppgaveFilterInfo")
                )
            }
        }
    }
}

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
    merkelapp = sak.merkelapp,
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
    merkelapp: String = "tag",
): HendelseModel.SakOpprettet {
    val uuid = uuid(id)
    val sakOpprettet = sakOpprettet(
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid,
        grupperingsid = uuid.toString(),
        merkelapp = merkelapp,
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



