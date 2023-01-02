package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class SorteringAvSakerPåFristTest : DescribeSpec({
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

    fun opprettOppgave(
        id: UUID,
        frist: LocalDate?,
    ): HendelseModel.OppgaveOpprettet {
        val oppgaveId = UUID.randomUUID()
        return HendelseModel.OppgaveOpprettet(
            hendelseId = oppgaveId,
            notifikasjonId = oppgaveId,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = id.toString(),
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
            påminnelse = null,
        )
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

    suspend fun opprettSakMedOppgaver(
        id: String,
        vararg frister: String?,
    ): UUID {
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

        frister.forEach { frist ->
            queryModel.oppdaterModellEtterHendelse(opprettOppgave(uuid, frist?.let { LocalDate.parse(it) }))
        }

        return uuid


    }

    describe("Sak som med oppgave") {
        /**
         * Sakene 2, 3, 4 og så 1 blir sortert etter frist, mens 6 og 8 vil bli sortert før 7 fordi disse har oppgaver.
         * sak 8 kommer før 6 fordi den er oppdatert sist.
         */
        val sak2 = opprettSakMedOppgaver("2", "2023-01-15", "2023-01-04")
        val sak3 = opprettSakMedOppgaver("3", "2023-01-05", "2023-01-25")
        val sak4 = opprettSakMedOppgaver("4", "2023-01-06", "2023-01-06")
        val sak1 = opprettSakMedOppgaver("1", "2023-01-10", "2023-01-10")
        val sak5 = opprettSakMedOppgaver("5", "2023-01-07", null)
        val sak6 = opprettSakMedOppgaver("6", null)
        val sak7 = opprettSakMedOppgaver("7")
        val sak8 = opprettSakMedOppgaver("8", null)

        val res = engine.hentSaker().getTypedContent<List<UUID>>("$.saker.saker.*.id")

        res shouldBe listOf(sak2, sak3, sak4, sak5, sak1, sak8, sak6, sak7)

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
                    }
                }
            }
        """.trimIndent()
    )

