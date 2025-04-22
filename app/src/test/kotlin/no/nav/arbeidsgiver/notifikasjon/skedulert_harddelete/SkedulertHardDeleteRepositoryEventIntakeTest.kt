package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyTidStrategi.FORLENG
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyTidStrategi.OVERSKRIV
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.getUuid
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private var idsuffixes = generateSequence(0) { it + 1 }.map { it.toString() }.iterator()

class SkedulertHardDeleteRepositoryEventIntakeTest {

    @Test
    fun `oppdaterModellEtterHendelse beskjed opprettet uten hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val hendelse = repository.beskjedOpprettet("1", "2020-01-01T01:01+00", null)

            // hard delete scheduleres ikke
            assertNull(repository.hent(hendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse oppgave opprettet uten hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val hendelse = repository.oppgaveOpprettet("2", "2020-01-01T01:01+00")

            // hard delete scheduleres ikke
            assertNull(repository.hent(hendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse sak opprettet uten hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val hendelse = repository.sakOpprettet(
                idsuffix = "3",
                mottattTidspunkt = "2020-01-01T01:01+00",
                hardDelete = null
            )

            // hard delete scheduleres ikke
            assertNull(repository.hent(hendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse beskjed opprettet med hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val hendelse = repository.beskjedOpprettet(
                idsuffix = "1",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                hardDelete = "2022-10-13T07:20:50.52"
            )

            // hard delete scheduleres
            assertNotNull(repository.hent(hendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse oppgave opprettet med hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val hendelse = repository.oppgaveOpprettet(
                idsuffix = "2",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                hardDelete = "2022-10-13T07:20:50.52"
            )

            // hard delete scheduleres
            assertNotNull(repository.hent(hendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse sak opprettet med hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val hendelse = repository.sakOpprettet(
                idsuffix = "3",
                mottattTidspunkt = "2020-01-01T01:01+00",
                hardDelete = "2022-10-13T07:20:50.52"
            )

            // hard delete scheduleres
            assertNotNull(repository.hent(hendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse oppgave utført uten hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            repository.oppgaveOpprettet("1", "2020-01-01T01:01+00")
            val utførtHendelse = repository.oppgaveUtført(
                idsuffix = "1",
                hardDelete = null,
                mottattTidspunkt = "2020-01-01T01:01:01.00Z"
            )

            // hard delete scheduleres ikke
            assertNull(repository.hent(utførtHendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse ny status sak uten hard delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            repository.sakOpprettet(
                idsuffix = "2",
                mottattTidspunkt = "2020-01-01T01:01+00",
                hardDelete = null
            )
            val nyStatusHendelse = repository.nyStatusSak(
                idsuffix = "2",
                mottattTidspunkt = "2020-01-01T01:01+00",
                hardDelete = null
            )

            // hard delete scheduleres ikke
            assertNull(repository.hent(nyStatusHendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse oppgave utført med hard delete uten tidligere skedulert delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            repository.oppgaveOpprettet("1", "2020-01-01T01:01+00", merkelapp = "hei")
            val utførtHendelse = repository.oppgaveUtført(
                idsuffix = "1",
                hardDelete = HendelseModel.HardDeleteUpdate(
                    nyTid = LocalDateTimeOrDuration.parse("2022-10-13T07:20:50.52"),
                    strategi = OVERSKRIV,
                ),
                mottattTidspunkt = "2020-01-01T01:01:01.00Z",
            )

            // hard delete scheduleres
            val skedulert = repository.hent(utførtHendelse.aggregateId)
            assertNotNull(skedulert)
            assertEquals("hei", skedulert.merkelapp)
        }

    @Test
    fun `oppdaterModellEtterHendelse ny status sak med hard delete uten tidligere skedulert delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            repository.sakOpprettet(
                idsuffix = "2",
                mottattTidspunkt = "2020-01-01T01:01+00",
                hardDelete = null
            )
            val nyStatusHendelse = repository.nyStatusSak(
                idsuffix = "2",
                mottattTidspunkt = "2020-01-01T01:01+00",
                hardDelete = HendelseModel.HardDeleteUpdate(
                    nyTid = LocalDateTimeOrDuration.parse("2022-10-13T07:20:50.52"),
                    strategi = OVERSKRIV,
                )
            )

            // hard delete scheduleres
            assertNotNull(repository.hent(nyStatusHendelse.aggregateId))
        }

    @Test
    fun `oppdaterModellEtterHendelse oppdater hendelse med hard delete og tidligere skedulert delete`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            suspend fun oppgaveUtførtCase(
                title: String,
                opprettetTidspunkt: String,
                opprinneligHardDelete: String,
                utførtTidspunkt: String,
                nyHardDelete: String,
                strategi: HendelseModel.NyTidStrategi,
                expected: Instant,
            ) {
                val nyTid = LocalDateTimeOrDuration.parse(nyHardDelete)
                val idsuffix = idsuffixes.next()


                repository.oppgaveOpprettet(
                    idsuffix = idsuffix,
                    opprettetTidspunkt = opprettetTidspunkt,
                    hardDelete = opprinneligHardDelete
                )
                val utførtHendelse = repository.oppgaveUtført(
                    idsuffix = idsuffix,
                    mottattTidspunkt = utførtTidspunkt,
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = nyTid,
                        strategi = strategi,
                    )
                )

                // hard delete scheduleres
                repository.hent(utførtHendelse.aggregateId).let {
                    assertNotNull(it, "$title skedulert hard delete skal ikke være null")
                    assertEquals(expected, it.beregnetSlettetidspunkt)
                }
            }

            suspend fun sakOppdatertCase(
                title: String,
                sakMottattTidspunkt: String,
                opprinneligHardDelete: String,
                oppdateringMottattTidspunkt: String,
                nyHardDelete: String,
                strategi: HendelseModel.NyTidStrategi,
                expected: Instant,
            ) {
                val nyIdsuffix = idsuffixes.next()

                repository.sakOpprettet(
                    idsuffix = nyIdsuffix,
                    mottattTidspunkt = sakMottattTidspunkt,
                    hardDelete = opprinneligHardDelete
                )
                val nyStatusHendelse = repository.nyStatusSak(
                    idsuffix = nyIdsuffix,
                    mottattTidspunkt = oppdateringMottattTidspunkt,
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.parse(nyHardDelete),
                        strategi = strategi,
                    )
                )

                repository.hent(nyStatusHendelse.aggregateId).let {
                    assertNotNull(it, "$title skedulert hard delete skal ikke være null")
                    assertEquals(expected, it.beregnetSlettetidspunkt)
                }
            }

            oppgaveUtførtCase(
                title = "forleng med absolutt dato, og opprinnelig er tidligere",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-08-13T07:20:50.52",
                nyHardDelete = "2022-09-13T07:20:50.52",
                utførtTidspunkt = "2020-01-01T01:01:01.42Z",
                strategi = FORLENG,
                expected = "2022-09-13T07:20:50.52".inOsloAsInstant(),
            )

            oppgaveUtførtCase(
                title = "forleng med absolutt dato, men opprinnelig er senere",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-10-13T07:20:50.52",
                nyHardDelete = "2022-09-13T07:20:50.52",
                utførtTidspunkt = "2020-01-01T01:01:01.42Z",
                strategi = FORLENG,
                expected = "2022-10-13T07:20:50.52".inOsloAsInstant(),
            )

            oppgaveUtførtCase(
                title = "forleng med relativ dato, men opprinnelig er senere",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-10-13T07:20:50.52",
                utførtTidspunkt = "2021-03-04T13:37:37.37Z",
                nyHardDelete = "P1YT1H",
                strategi = FORLENG,
                expected = "2022-10-13T07:20:50.52".inOsloAsInstant(),
            )

            oppgaveUtførtCase(
                title = "forleng med relativ dato, men opprinnelig er tidligere",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-10-13T07:20:50.52",
                utførtTidspunkt = "2021-03-04T13:37:37.37Z",
                nyHardDelete = "P1Y8MT1H",
                strategi = FORLENG,
                expected = Instant.parse("2022-11-04T14:37:37.37Z"),
            )

            listOf(
                "2020-10-13T07:20:50.52",
                "2022-10-13T07:20:50.52",
                "2025-10-13T07:20:50.52",
            ).forEach { opprinneligHardDelete ->
                oppgaveUtførtCase(
                    title = "overskriv med absolutt dato",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    opprinneligHardDelete = opprinneligHardDelete,
                    nyHardDelete = "2022-09-13T07:20:50.52",
                    utførtTidspunkt = "2020-01-01T01:01:01.42Z",
                    strategi = OVERSKRIV,
                    expected = "2022-09-13T07:20:50.52".inOsloAsInstant(),
                )

                oppgaveUtførtCase(
                    title = "overskriv med relativ dato",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    opprinneligHardDelete = opprinneligHardDelete,
                    utførtTidspunkt = "2021-03-04T13:37:37.37Z",
                    nyHardDelete = "P1YT1H",
                    strategi = OVERSKRIV,
                    expected = Instant.parse("2022-03-04T14:37:37.37Z"),
                )

                sakOppdatertCase(
                    title = "overskriv med absolutt dato",
                    sakMottattTidspunkt = "2020-01-01T01:01+00",
                    opprinneligHardDelete = opprinneligHardDelete,
                    oppdateringMottattTidspunkt = "2020-01-01T01:01+00",
                    nyHardDelete = "2022-09-13T07:20:50.52",
                    strategi = OVERSKRIV,
                    expected = "2022-09-13T07:20:50.52".inOsloAsInstant()
                )

                sakOppdatertCase(
                    title = "overskriv med offset",
                    sakMottattTidspunkt = "2020-01-01T01:01+00",
                    opprinneligHardDelete = opprinneligHardDelete,
                    oppdateringMottattTidspunkt = "2020-01-01T01:01+00",
                    nyHardDelete = "P1YT1H",
                    strategi = OVERSKRIV,
                    expected = Instant.parse("2021-01-01T02:01:00.00Z"),
                )
            }

            sakOppdatertCase(
                title = "forleng med absolutt dato, men opprinnelig er senere",
                sakMottattTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-10-13T07:20:50.52",
                oppdateringMottattTidspunkt = "2020-01-01T01:01+00",
                nyHardDelete = "2022-09-13T07:20:50.52",
                strategi = FORLENG,
                expected = "2022-10-13T07:20:50.52".inOsloAsInstant(),
            )

            sakOppdatertCase(
                title = "forleng med absolutt dato, og opprinnelig er tidligere",
                sakMottattTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-10-13T07:20:50.52",
                oppdateringMottattTidspunkt = "2020-01-01T01:01+00",
                nyHardDelete = "2023-09-13T07:20:50.52",
                strategi = FORLENG,
                expected = "2023-09-13T07:20:50.52".inOsloAsInstant()
            )

            sakOppdatertCase(
                title = "forleng med relativ dato, og opprinnelig er tidligere",
                sakMottattTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-10-13T07:20:50.52",
                oppdateringMottattTidspunkt = "2020-01-01T01:01+00",
                nyHardDelete = "P3YT1H",
                strategi = FORLENG,
                expected = Instant.parse("2023-01-01T02:01:00.00Z"),
            )

            sakOppdatertCase(
                title = "forleng med relativ dato, men opprinnelig er senere",
                sakMottattTidspunkt = "2020-01-01T01:01+00",
                opprinneligHardDelete = "2022-10-13T07:20:50.52",
                oppdateringMottattTidspunkt = "2020-01-01T01:01+00",
                nyHardDelete = "P1YT1H",
                strategi = FORLENG,
                expected = "2022-10-13T07:20:50.52".inOsloAsInstant()
            )
        }

    @Test
    fun `Hard delete slette oppgave`() = withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
        val repository = SkedulertHardDeleteRepositoryImpl(database)
        val hendelse = repository.oppgaveOpprettet(
            idsuffix = "1",
            opprettetTidspunkt = "2020-01-01T01:01+00",
            hardDelete = "2022-10-13T07:20:50.52"
        )

        repository.hardDelete("1")

        // should be deleted
        assertNull(repository.hent(hendelse.aggregateId))
    }

    @Test
    fun `Hard delete slette sak`() = withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
        val repository = SkedulertHardDeleteRepositoryImpl(database)
        val hendelse = repository.sakOpprettet(
            idsuffix = "2",
            mottattTidspunkt = "2020-01-01T01:01+00",
            hardDelete = "2022-10-13T07:20:50.52"
        )

        repository.hardDelete("2")

        // should be deleted
        assertNull(repository.hent(hendelse.aggregateId))
    }

    @Test
    fun `Hard delete slette sak med tilhørende beskjed og oppgave`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val sak = repository.sakOpprettet(
                idsuffix = "1",
                merkelapp = "angela",
                grupperingsid = "s42",
                mottattTidspunkt = "2020-01-01T01:01+00",
                hardDelete = "2022-10-13T07:20:50.52"
            )
            val oppgaveMedSak = repository.oppgaveOpprettet(
                idsuffix = "2",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                sakId = sak.sakId,
                merkelapp = sak.merkelapp,
                grupperingsid = sak.grupperingsid,
            )
            val oppgaveUtenSak = repository.oppgaveOpprettet(
                idsuffix = "3",
                opprettetTidspunkt = "2020-01-01T01:01+00"
            )
            val beskjedMedSak = repository.beskjedOpprettet(
                idsuffix = "4",
                opprettetTidspunkt = "2020-01-01T01:01+00",
                sakId = sak.sakId,
                merkelapp = sak.merkelapp,
                grupperingsid = sak.grupperingsid,
            )
            val beskjedUtenSak = repository.beskjedOpprettet(
                idsuffix = "5",
                opprettetTidspunkt = "2020-01-01T01:01+00",
            )
            repository.hardDelete("1", merkelapp = sak.merkelapp, grupperingsid = sak.grupperingsid)

            // sak er slettet med tilhørende beskjed og oppgave
            assertNull(database.hentAggregate(sak.aggregateId))
            assertNull(database.hentAggregate(oppgaveMedSak.aggregateId))
            assertNull(database.hentAggregate(beskjedMedSak.aggregateId))

            // beskjed og oppgave som ikke hører til sak er ikke slettet
            assertNotNull(database.hentAggregate(oppgaveUtenSak.aggregateId))
            assertNotNull(database.hentAggregate(beskjedUtenSak.aggregateId))
        }

    @Test
    fun `Hard delete ikke slette andre saker`() = withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
        val repository = SkedulertHardDeleteRepositoryImpl(database)
        val hendelse = repository.sakOpprettet(
            idsuffix = "3",
            mottattTidspunkt = "2020-01-01T01:01+00",
            hardDelete = "2022-10-13T07:20:50.52"
        )
        repository.sakOpprettet(
            idsuffix = "4",
            mottattTidspunkt = "2020-01-01T01:01+00",
            hardDelete = "2022-10-13T07:20:50.52"
        )
        repository.hardDelete("4")

        // should not be deleted
        assertNotNull(repository.hent(hendelse.aggregateId))
    }

    @Test
    fun `Hard delete utføres for en sak som ikke lenger finnes`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            // for eksempel midt i en replay

            assertDoesNotThrow {
                repository.hardDelete("1")
            }
        }
}


private suspend fun <T : HendelseModel.Hendelse> SkedulertHardDeleteRepositoryImpl.oppdaterModell(
    hendelse: T,
    timestamp: Instant = Instant.EPOCH,
): T =
    hendelse.also { oppdaterModellEtterHendelse(it, timestamp) }

private suspend fun SkedulertHardDeleteRepositoryImpl.hardDelete(
    idsuffix: String,
    merkelapp: String? = null,
    grupperingsid: String? = null,
) = oppdaterModell(
    HendelseModel.HardDelete(
        virksomhetsnummer = idsuffix,
        aggregateId = uuid(idsuffix),
        hendelseId = UUID.randomUUID(),
        produsentId = idsuffix,
        kildeAppNavn = "test-app",
        deletedAt = OffsetDateTime.now(),
        merkelapp = merkelapp,
        grupperingsid = grupperingsid,
    )
)

private suspend fun SkedulertHardDeleteRepositoryImpl.beskjedOpprettet(
    idsuffix: String,
    opprettetTidspunkt: String,
    hardDelete: String? = null,
    merkelapp: String = "merkelapp",
    grupperingsid: String? = null,
    sakId: UUID? = null,
): HendelseModel.BeskjedOpprettet = oppdaterModell(
    HendelseModel.BeskjedOpprettet(
        virksomhetsnummer = idsuffix,
        notifikasjonId = uuid(idsuffix),
        hendelseId = uuid(idsuffix),
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        merkelapp = merkelapp,
        eksternId = idsuffix,
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = idsuffix,
                serviceCode = idsuffix,
                serviceEdition = idsuffix
            )
        ),
        tekst = idsuffix,
        grupperingsid = grupperingsid,
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse(opprettetTidspunkt),
        eksterneVarsler = listOf(),
        hardDelete = hardDelete?.let { LocalDateTimeOrDuration.parse(it) },
        sakId = sakId,
    )
)


private suspend fun SkedulertHardDeleteRepositoryImpl.oppgaveOpprettet(
    idsuffix: String,
    opprettetTidspunkt: String,
    merkelapp: String = "merkelapp",
    hardDelete: String? = null,
    grupperingsid: String? = null,
    sakId: UUID? = null,
): HendelseModel.OppgaveOpprettet = oppdaterModell(
    HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = idsuffix,
        notifikasjonId = uuid(idsuffix),
        hendelseId = uuid(idsuffix),
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        merkelapp = merkelapp,
        eksternId = idsuffix,
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = idsuffix,
                serviceCode = idsuffix,
                serviceEdition = idsuffix
            )
        ),
        tekst = idsuffix,
        grupperingsid = grupperingsid,
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse(opprettetTidspunkt),
        eksterneVarsler = listOf(),
        hardDelete = hardDelete?.let { LocalDateTimeOrDuration.parse(it) },
        frist = null,
        påminnelse = null,
        sakId = sakId,
    )
)


private suspend fun SkedulertHardDeleteRepositoryImpl.sakOpprettet(
    idsuffix: String,
    mottattTidspunkt: String,
    oppgittTidspunkt: String? = null,
    merkelapp: String = "merkelapp",
    hardDelete: String?,
    grupperingsid: String = idsuffix,
): HendelseModel.SakOpprettet = oppdaterModell(
    HendelseModel.SakOpprettet(
        virksomhetsnummer = idsuffix,
        sakId = uuid(idsuffix),
        hendelseId = UUID.randomUUID(),
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        merkelapp = merkelapp,
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = idsuffix,
                serviceCode = idsuffix,
                serviceEdition = idsuffix
            )
        ),
        grupperingsid = grupperingsid,
        lenke = "",
        mottattTidspunkt = OffsetDateTime.parse(mottattTidspunkt),
        oppgittTidspunkt = oppgittTidspunkt?.let { OffsetDateTime.parse(it) },
        hardDelete = hardDelete?.let { LocalDateTimeOrDuration.parse(it) },
        nesteSteg = null,
        tittel = "",
        tilleggsinformasjon = null
    )
)

private suspend fun SkedulertHardDeleteRepositoryImpl.oppgaveUtført(
    idsuffix: String,
    mottattTidspunkt: String,
    hardDelete: HendelseModel.HardDeleteUpdate?
): HendelseModel.OppgaveUtført = oppdaterModell(
    HendelseModel.OppgaveUtført(
        virksomhetsnummer = idsuffix,
        notifikasjonId = uuid(idsuffix),
        hendelseId = UUID.randomUUID(),
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        hardDelete = hardDelete,
        nyLenke = null,
        utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
    ),
    Instant.parse(mottattTidspunkt)
)

private suspend fun SkedulertHardDeleteRepositoryImpl.nyStatusSak(
    idsuffix: String,
    mottattTidspunkt: String,
    oppgittTidspunkt: String? = null,
    hardDelete: HendelseModel.HardDeleteUpdate?
): HendelseModel.NyStatusSak = oppdaterModell(
    HendelseModel.NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = idsuffix,
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        sakId = uuid(idsuffix),
        status = HendelseModel.SakStatus.UNDER_BEHANDLING,
        overstyrStatustekstMed = null,
        mottattTidspunkt = OffsetDateTime.parse(mottattTidspunkt),
        oppgittTidspunkt = oppgittTidspunkt?.let { OffsetDateTime.parse(it) },
        idempotensKey = IdempotenceKey.initial(),
        hardDelete = hardDelete,
        nyLenkeTilSak = null,
    )
)

suspend fun Database.hentAggregate(aggregateId: UUID): UUID? {
    return nonTransactionalExecuteQuery(
        """
            select 
                aggregate_id 
            from aggregate
            where aggregate_id = ?
        """, {
            uuid(aggregateId)
        }) {
        getUuid("aggregate_id")
    }.firstOrNull()
}

