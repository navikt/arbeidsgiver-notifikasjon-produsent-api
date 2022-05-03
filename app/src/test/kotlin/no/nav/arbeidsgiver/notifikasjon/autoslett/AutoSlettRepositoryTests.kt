package no.nav.arbeidsgiver.notifikasjon.autoslett

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.AutoSlett
import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class AutoSlettRepositoryTests : DescribeSpec({
    val database = testDatabase(AutoSlett.databaseConfig)
    val repository = AutoSlettRepository(database)

    describe("AutoSlettRepository#oppdaterModellEtterHendelse") {
        context("opprett hendelse uten hard delete") {
            it("beskjed opprettet") {
                val hendelse = beskjedOpprettet("1", "2020-01-01T01:01+00", null)
                repository.oppdaterModellEtterHendelse(hendelse)

                it("hard delete scheduleres ikke") {
                    repository.hent(hendelse.aggregateId) shouldBe null
                }
            }

            it("oppgave opprettet") {
                val hendelse = oppgaveOpprettet("2", "2020-01-01T01:01+00", null)
                repository.oppdaterModellEtterHendelse(hendelse)

                it("hard delete scheduleres ikke") {
                    repository.hent(hendelse.aggregateId) shouldBe null
                }
            }

            it("sak opprettet") {
                val hendelse = sakOpprettet(
                    idsuffix = "3",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = null
                )
                repository.oppdaterModellEtterHendelse(hendelse)

                it("hard delete scheduleres ikke") {
                    repository.hent(hendelse.aggregateId) shouldBe null
                }
            }
        }

        context("opprett hendelse med hard delete") {
            it("beskjed opprettet") {
                val hendelse = beskjedOpprettet(
                    idsuffix = "1",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = "2022-10-13T07:20:50.52"
                )
                repository.oppdaterModellEtterHendelse(hendelse)

                it("hard delete scheduleres") {
                    repository.hent(hendelse.aggregateId) shouldNotBe null
                }
            }

            it("oppgave opprettet") {
                val hendelse = oppgaveOpprettet(
                    idsuffix = "2",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = "2022-10-13T07:20:50.52"
                )
                repository.oppdaterModellEtterHendelse(hendelse)

                it("hard delete scheduleres") {
                    repository.hent(hendelse.aggregateId) shouldNotBe null
                }
            }

            it("sak opprettet") {
                val hendelse = sakOpprettet(
                    idsuffix = "3",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = "2022-10-13T07:20:50.52"
                )
                repository.oppdaterModellEtterHendelse(hendelse)

                it("hard delete scheduleres") {
                    repository.hent(hendelse.aggregateId) shouldNotBe null
                }
            }
        }

        context("oppdater hendelse uten hard delete") {
            it("oppgave utført") {
                val opprettHendelse = oppgaveOpprettet("1", "2020-01-01T01:01+00", null)
                val utførtHendelse = oppgaveUtført("1", hardDelete = null)
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(utførtHendelse)

                it("hard delete scheduleres ikke") {
                    repository.hent(utførtHendelse.aggregateId) shouldBe null
                }
            }
            it("ny status sak") {
                val opprettHendelse = sakOpprettet(
                    idsuffix = "2",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = null
                )
                val nyStatusHendelse =
                    nyStatusSak(idsuffix = "2", mottattTidspunkt = "2020-01-01T01:01+00", hardDelete = null)
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(nyStatusHendelse)

                it("hard delete scheduleres ikke") {
                    repository.hent(nyStatusHendelse.aggregateId) shouldBe null
                }
            }
        }

        context("oppdater hendelse med hard delete uten tidligere skedulert delete") {
            it("oppgave utført") {
                val opprettHendelse = oppgaveOpprettet("1", "2020-01-01T01:01+00", null)
                val utførtHendelse = oppgaveUtført(
                    "1", hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2022-10-13T07:20:50.52")),
                        strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
                    )
                )
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(utførtHendelse)

                it("hard delete scheduleres") {
                    repository.hent(utførtHendelse.aggregateId) shouldNotBe null
                }
            }
            it("ny status sak") {
                val opprettHendelse = sakOpprettet(
                    idsuffix = "2",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = null
                )
                val nyStatusHendelse = nyStatusSak(
                    idsuffix = "2",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2022-10-13T07:20:50.52")),
                        strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
                    )
                )
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(nyStatusHendelse)

                it("hard delete scheduleres ikke") {
                    repository.hent(nyStatusHendelse.aggregateId) shouldNotBe null
                }
            }
        }

        context("oppdater hendelse med hard delete og tidligere skedulert delete") {
            it("oppgave utført strategi overskriv") {
                val opprinneligHardDelete = "2022-10-13T07:20:50.52"
                val nyHardDelete = "2022-09-13T07:20:50.52"
                val strategi = HendelseModel.NyTidStrategi.OVERSKRIV

                val opprettHendelse = oppgaveOpprettet(
                    idsuffix = "1",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = opprinneligHardDelete
                )
                val utførtHendelse = oppgaveUtført(
                    idsuffix = "1",
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse(nyHardDelete)),
                        strategi = strategi,
                    )
                )
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(utførtHendelse)

                it("hard delete scheduleres") {
                    val skedulert = repository.hent(utførtHendelse.aggregateId)
                    skedulert shouldNotBe null
                    skedulert!!.beregnetSlettetidspunkt shouldBe LocalDateTime.parse(nyHardDelete).atOslo().toInstant()
                }
            }

            it("oppgave utført strategi forleng") {
                val opprinneligHardDelete = "2022-10-13T07:20:50.52"
                val nyHardDelete = "2022-09-13T07:20:50.52"
                val strategi = HendelseModel.NyTidStrategi.FORLENG

                val opprettHendelse = oppgaveOpprettet(
                    idsuffix = "2",
                    opprettetTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = opprinneligHardDelete
                )
                val utførtHendelse = oppgaveUtført(
                    idsuffix = "2",
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse(nyHardDelete)),
                        strategi = strategi,
                    )
                )
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(utførtHendelse)

                it("hard delete scheduleres") {
                    val skedulert = repository.hent(utførtHendelse.aggregateId)
                    skedulert shouldNotBe null
                    skedulert!!.beregnetSlettetidspunkt shouldBe LocalDateTime.parse(opprinneligHardDelete).atOslo()
                        .toInstant()
                }
            }

            it("ny status sak strategi overskriv") {
                val opprinneligHardDelete = "2022-10-13T07:20:50.52"
                val nyHardDelete = "2022-09-13T07:20:50.52"
                val strategi = HendelseModel.NyTidStrategi.OVERSKRIV

                val opprettHendelse = sakOpprettet(
                    idsuffix = "3",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = opprinneligHardDelete
                )
                val nyStatusHendelse = nyStatusSak(
                    idsuffix = "3",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse(nyHardDelete)),
                        strategi = strategi,
                    )
                )
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(nyStatusHendelse)

                it("hard delete scheduleres ikke") {
                    val skedulertHardDelete = repository.hent(nyStatusHendelse.aggregateId)
                    skedulertHardDelete shouldNotBe null
                    skedulertHardDelete!!.beregnetSlettetidspunkt shouldBe LocalDateTime.parse(nyHardDelete).atOslo()
                        .toInstant()
                }
            }

            it("ny status sak strategi forleng") {
                val opprinneligHardDelete = "2022-10-13T07:20:50.52"
                val nyHardDelete = "2022-09-13T07:20:50.52"
                val strategi = HendelseModel.NyTidStrategi.FORLENG

                val opprettHendelse = sakOpprettet(
                    idsuffix = "4",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = opprinneligHardDelete
                )
                val nyStatusHendelse = nyStatusSak(
                    idsuffix = "4",
                    mottattTidspunkt = "2020-01-01T01:01+00",
                    hardDelete = HendelseModel.HardDeleteUpdate(
                        nyTid = LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse(nyHardDelete)),
                        strategi = strategi,
                    )
                )
                repository.oppdaterModellEtterHendelse(opprettHendelse)
                repository.oppdaterModellEtterHendelse(nyStatusHendelse)

                it("hard delete scheduleres ikke") {
                    val skedulertHardDelete = repository.hent(nyStatusHendelse.aggregateId)
                    skedulertHardDelete shouldNotBe null
                    skedulertHardDelete!!.beregnetSlettetidspunkt shouldBe LocalDateTime.parse(opprinneligHardDelete)
                        .atOslo().toInstant()
                }
            }
        }
    }
})

private fun beskjedOpprettet(
    idsuffix: String,
    opprettetTidspunkt: String,
    hardDelete: String?
) = HendelseModel.BeskjedOpprettet(
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

private fun oppgaveOpprettet(
    idsuffix: String,
    opprettetTidspunkt: String,
    hardDelete: String?
) = HendelseModel.OppgaveOpprettet(
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

private fun sakOpprettet(
    idsuffix: String,
    mottattTidspunkt: String,
    oppgittTidspunkt: String? = null,
    hardDelete: String?
) = HendelseModel.SakOpprettet(
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

private fun oppgaveUtført(
    idsuffix: String,
    hardDelete: HendelseModel.HardDeleteUpdate?
) = HendelseModel.OppgaveUtført(
    virksomhetsnummer = idsuffix,
    notifikasjonId = uuid(idsuffix),
    hendelseId = UUID.randomUUID(),
    produsentId = idsuffix,
    kildeAppNavn = idsuffix,
    hardDelete = hardDelete,
)

private fun nyStatusSak(
    idsuffix: String,
    mottattTidspunkt: String,
    oppgittTidspunkt: String? = null,
    hardDelete: HendelseModel.HardDeleteUpdate?
) = HendelseModel.NyStatusSak(
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
