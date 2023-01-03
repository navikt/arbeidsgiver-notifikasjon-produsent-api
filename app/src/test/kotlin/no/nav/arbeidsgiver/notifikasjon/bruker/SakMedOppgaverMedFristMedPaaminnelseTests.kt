package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class SakMedOppgaverMedFristMedPaaminnelseTests : DescribeSpec({
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

    suspend fun opprettOppgave(
        grupperingsid: String,
        frist: LocalDate?,
        paaminnelse: HendelseModel.Påminnelse,
    ) {
        val oppgaveId = UUID.randomUUID()

        HendelseModel.OppgaveOpprettet(
            hendelseId = oppgaveId,
            notifikasjonId = oppgaveId,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = grupperingsid,
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
            påminnelse = paaminnelse,
        ).also { queryModel.oppdaterModellEtterHendelse(it) }

        HendelseModel.PåminnelseOpprettet(
            virksomhetsnummer = "1",
            hendelseId = UUID.randomUUID(),
            produsentId = "1",
            kildeAppNavn = "1",
            notifikasjonId = oppgaveId,
            opprettetTidpunkt = Instant.now(),
            oppgaveOpprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00").toInstant(),
            frist = frist,
            tidspunkt = paaminnelse.tidspunkt,
            eksterneVarsler = listOf(),
        ).also { queryModel.oppdaterModellEtterHendelse(it) }
    }

    fun opprettStatus(id: UUID) = HendelseModel.NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = id,
        status = HendelseModel.SakStatus.MOTTATT,
        overstyrStatustekstMed = null,
        oppgittTidspunkt = null,
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
        hardDelete = null,
        nyLenkeTilSak = null,
    )

    suspend fun opprettSak(
        id: String,
    ): String {
        val uuid = uuid(id)
        val sakOpprettet = HendelseModel.SakOpprettet(
            hendelseId = uuid,
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

        queryModel.oppdaterModellEtterHendelse(sakOpprettet)
        queryModel.oppdaterModellEtterHendelse(opprettStatus(uuid))

        return uuid.toString()
    }

    describe("Sak med oppgave med frist og påminnelse") {
        val påminnelsestidspunktLocalDateTime = LocalDateTime.parse("2023-01-02T12:15:00")
        val sak1 = opprettSak("1")
        opprettOppgave(
            sak1,
            LocalDate.parse("2023-01-15"),
            HendelseModel.Påminnelse(
                HendelseModel.PåminnelseTidspunkt.Konkret(
                    påminnelsestidspunktLocalDateTime,
                    påminnelsestidspunktLocalDateTime.inOsloAsInstant()
                ),
                emptyList()
            )
        )

        val res =
            engine.hentSaker().getTypedContent<List<OffsetDateTime>>("$.saker.saker.*.oppgaver.*.paaminnelseTidspunkt")

        res.first().inOsloLocalDateTime() shouldBe påminnelsestidspunktLocalDateTime
    }
}
)

private fun TestApplicationEngine.hentSaker(): TestApplicationResponse =
    brukerApi(
        """
            {
                saker (virksomhetsnummer: "1", limit: 10 , sortering: FRIST ){
                    saker {
                        id
                        oppgaver {
                            paaminnelseTidspunkt   
                            frist
                            tilstand
                        }
                    }
                }
            }
        """.trimIndent()
    )

