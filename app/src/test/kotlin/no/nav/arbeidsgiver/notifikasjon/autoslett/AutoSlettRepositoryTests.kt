package no.nav.arbeidsgiver.notifikasjon.autoslett

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerContext
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.AutoSlett
import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyTidStrategi.FORLENG
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyTidStrategi.OVERSKRIV
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

private var idsuffixes = generateSequence(0) { it + 1 }.map { it.toString() }.iterator()

class AutoSlettRepositoryTests : DescribeSpec({
    val database = testDatabase(AutoSlett.databaseConfig)
    val repository = AutoSlettRepository(database)

    suspend fun DescribeSpecContainerContext.oppgaveUtførtCase(
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
        it("oppgave utført $title") {
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
            it("hard delete scheduleres") {
                val skedulert = repository.hent(utførtHendelse.aggregateId)
                skedulert shouldNotBe null
                skedulert!!.beregnetSlettetidspunkt shouldBe expected
            }
        }
    }

    suspend fun DescribeSpecContainerContext.sakOppdatertCase(
        title: String,
        sakMottattTidspunkt: String,
        opprinneligHardDelete: String,
        oppdateringMottattTidspunkt: String,
        nyHardDelete: String,
        strategi: HendelseModel.NyTidStrategi,
        expected: Instant,
    ) {
        val nyTid = LocalDateTimeOrDuration.parse(nyHardDelete)
        val idsuffix = idsuffixes.next()
        it("ny status sak $title") {
            repository.sakOpprettet(
                idsuffix = idsuffix,
                mottattTidspunkt = sakMottattTidspunkt,
                hardDelete = opprinneligHardDelete
            )
            val nyStatusHendelse = repository.nyStatusSak(
                idsuffix = idsuffix,
                mottattTidspunkt = oppdateringMottattTidspunkt,
                hardDelete = HendelseModel.HardDeleteUpdate(
                    nyTid = nyTid,
                    strategi = strategi,
                )
            )

            it("hard delete er $expected") {
                val skedulertHardDelete = repository.hent(nyStatusHendelse.aggregateId)
                skedulertHardDelete shouldNotBe null
                skedulertHardDelete!!.beregnetSlettetidspunkt shouldBe expected
            }
        }
    }

    describe("AutoSlettRepository#oppdaterModellEtterHendelse") {
        context("opprett hendelse uten hard delete") {
            it("beskjed opprettet") {
                val hendelse = repository.beskjedOpprettet("1", "2020-01-01T01:01+00", null)

                it("hard delete scheduleres ikke") {
                    repository.hent(hendelse.aggregateId) shouldBe null
                }
            }

            it("oppgave opprettet") {
                val hendelse = repository.oppgaveOpprettet("2", "2020-01-01T01:01+00", null)

                it("hard delete scheduleres ikke") {
                    repository.hent(hendelse.aggregateId) shouldBe null
                }
            }

            it("sak opprettet") {
                val hendelse = repository.sakOpprettet(
                    idsuffix = "3",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = null
                )

                it("hard delete scheduleres ikke") {
                    repository.hent(hendelse.aggregateId) shouldBe null
                }
            }
        }

        context("opprett hendelse med hard delete") {
            it("beskjed opprettet") {
                val hendelse = repository.beskjedOpprettet(
                    idsuffix = "1",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = "2022-10-13T07:20:50.52"
                )

                it("hard delete scheduleres") {
                    repository.hent(hendelse.aggregateId) shouldNotBe null
                }
            }

            it("oppgave opprettet") {
                val hendelse = repository.oppgaveOpprettet(
                    idsuffix = "2",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = "2022-10-13T07:20:50.52"
                )

                it("hard delete scheduleres") {
                    repository.hent(hendelse.aggregateId) shouldNotBe null
                }
            }

            it("sak opprettet") {
                val hendelse = repository.sakOpprettet(
                    idsuffix = "3",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = "2022-10-13T07:20:50.52"
                )

                it("hard delete scheduleres") {
                    repository.hent(hendelse.aggregateId) shouldNotBe null
                }
            }
        }

        context("oppdater hendelse uten hard delete") {
            it("oppgave utført") {
                repository.oppgaveOpprettet("1", "2020-01-01T01:01+00", null)
                val utførtHendelse = repository.oppgaveUtført(
                    idsuffix = "1",
                    hardDelete = null,
                    mottattTidspunkt = "2020-01-01T01:01:01.00Z"
                )

                it("hard delete scheduleres ikke") {
                    repository.hent(utførtHendelse.aggregateId) shouldBe null
                }
            }
            it("ny status sak") {
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

                it("hard delete scheduleres ikke") {
                    repository.hent(nyStatusHendelse.aggregateId) shouldBe null
                }
            }
        }

        context("oppdater hendelse med hard delete uten tidligere skedulert delete") {
            it("oppgave utført") {
                repository.oppgaveOpprettet("1", "2020-01-01T01:01+00", null)
                val utførtHendelse = repository.oppgaveUtført(
                    idsuffix = "1",
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.parse("2022-10-13T07:20:50.52"),
                        strategi = OVERSKRIV,
                    ),
                    mottattTidspunkt = "2020-01-01T01:01:01.00Z",
                )

                it("hard delete scheduleres") {
                    repository.hent(utførtHendelse.aggregateId) shouldNotBe null
                }
            }
            it("ny status sak") {
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

                it("hard delete scheduleres ikke") {
                    repository.hent(nyStatusHendelse.aggregateId) shouldNotBe null
                }
            }
        }

        context("oppdater hendelse med hard delete og tidligere skedulert delete") {
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

            forAll(
                "2020-10-13T07:20:50.52",
                "2022-10-13T07:20:50.52",
                "2025-10-13T07:20:50.52",
            ) { opprinneligHardDelete ->
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
                    opprinneligHardDelete = "2022-10-13T07:20:50.52",
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
    }
})

private suspend fun <T : HendelseModel.Hendelse> AutoSlettRepository.oppdaterModell(
    hendelse: T,
    timestamp: Instant = Instant.EPOCH,
): T =
    hendelse.also { oppdaterModellEtterHendelse(it, timestamp) }

private suspend fun AutoSlettRepository.beskjedOpprettet(
    idsuffix: String,
    opprettetTidspunkt: String,
    hardDelete: String?
): HendelseModel.BeskjedOpprettet = oppdaterModell(
    HendelseModel.BeskjedOpprettet(
        virksomhetsnummer = idsuffix,
        notifikasjonId = uuid(idsuffix),
        hendelseId = UUID.randomUUID(),
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        merkelapp = idsuffix,
        eksternId = idsuffix,
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = idsuffix,
                serviceCode = idsuffix,
                serviceEdition = idsuffix
            )
        ),
        tekst = idsuffix,
        grupperingsid = null,
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse(opprettetTidspunkt),
        eksterneVarsler = listOf(),
        hardDelete = hardDelete?.let { LocalDateTimeOrDuration.parse(it) },
    )
)


private suspend fun AutoSlettRepository.oppgaveOpprettet(
    idsuffix: String,
    opprettetTidspunkt: String,
    hardDelete: String?
): HendelseModel.OppgaveOpprettet = oppdaterModell(
    HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = idsuffix,
        notifikasjonId = uuid(idsuffix),
        hendelseId = UUID.randomUUID(),
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        merkelapp = idsuffix,
        eksternId = idsuffix,
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = idsuffix,
                serviceCode = idsuffix,
                serviceEdition = idsuffix
            )
        ),
        tekst = idsuffix,
        grupperingsid = null,
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse(opprettetTidspunkt),
        eksterneVarsler = listOf(),
        hardDelete = hardDelete?.let { LocalDateTimeOrDuration.parse(it) },
    )
)


private suspend fun AutoSlettRepository.sakOpprettet(
    idsuffix: String,
    mottattTidspunkt: String,
    oppgittTidspunkt: String? = null,
    hardDelete: String?
): HendelseModel.SakOpprettet = oppdaterModell(
    HendelseModel.SakOpprettet(
        virksomhetsnummer = idsuffix,
        sakId = uuid(idsuffix),
        hendelseId = UUID.randomUUID(),
        produsentId = idsuffix,
        kildeAppNavn = idsuffix,
        merkelapp = idsuffix,
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = idsuffix,
                serviceCode = idsuffix,
                serviceEdition = idsuffix
            )
        ),
        grupperingsid = idsuffix,
        lenke = "",
        mottattTidspunkt = OffsetDateTime.parse(mottattTidspunkt),
        oppgittTidspunkt = oppgittTidspunkt?.let { OffsetDateTime.parse(it) },
        hardDelete = hardDelete?.let { LocalDateTimeOrDuration.parse(it) },
        tittel = "",
    )
)

private suspend fun AutoSlettRepository.oppgaveUtført(
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
    ),
    Instant.parse(mottattTidspunkt)
)

private suspend fun AutoSlettRepository.nyStatusSak(
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

