package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime

class QueryNotifikasjonerMedSakTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
        }
    )

    context("Query.notifikasjoner med sak") {
        val opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00")

        val oppgaveUtenSakOpprettet = brukerRepository.oppgaveOpprettet(
            grupperingsid = "0",
            opprettetTidspunkt = opprettetTidspunkt,
            tekst = "oppgave uten sak",
            frist = LocalDate.parse("2007-12-03"),
        )

        val beskjedUtenSakOpprettet = brukerRepository.beskjedOpprettet(
            grupperingsid = "1",
            opprettetTidspunkt = opprettetTidspunkt.minusHours(1),
            tekst = "beskjed uten sak",
        )

        val oppgaveMedSakOpprettet = brukerRepository.oppgaveOpprettet(
            grupperingsid = "2",
            opprettetTidspunkt = opprettetTidspunkt.minusHours(2),
            tekst = "oppgave uten sak",
            frist = LocalDate.parse("2007-12-03"),
        ).also {
            brukerRepository.sakOpprettet(
                grupperingsid = it.grupperingsid!!,
                mottakere = it.mottakere,
                tittel = "Sakstittel for oppgave",
                mottattTidspunkt = OffsetDateTime.now(),
            )
        }
        
        val beskjedMedSakOpprettet = brukerRepository.beskjedOpprettet(
            grupperingsid = "3",
            opprettetTidspunkt = opprettetTidspunkt.minusHours(3),
            tekst = "beskjed med sak",
        ).also {
            brukerRepository.sakOpprettet(
                grupperingsid = it.grupperingsid!!,
                mottakere = it.mottakere,
                tittel = "Sakstittel for beskjed",
                mottattTidspunkt = OffsetDateTime.now(),
            )
        }
        
        val oppgaveMedSakFeilMottakerOpprettet = brukerRepository.oppgaveOpprettet(
            grupperingsid = "4",
            opprettetTidspunkt = opprettetTidspunkt.minusHours(4),
            tekst = "oppgave med sak feil mottaker",
            frist = LocalDate.parse("2007-12-03"),
        ).also {
            brukerRepository.sakOpprettet(
                grupperingsid = it.grupperingsid!!,
                virksomhetsnummer = TEST_VIRKSOMHET_2,
                mottakere = listOf(TEST_MOTTAKER_2),
                tittel = "Sakstittel for oppgave feil mottaker",
                mottattTidspunkt = OffsetDateTime.now(),
            )
        }
        
        val beskjedMedSakMedFeilMottakerOpprettet = brukerRepository.beskjedOpprettet(
            grupperingsid = "5",
            opprettetTidspunkt = opprettetTidspunkt.minusHours(5),
            tekst = "beskjed med sak feil mottaker",
        ).also {
            brukerRepository.sakOpprettet(
                grupperingsid = it.grupperingsid!!,
                virksomhetsnummer = TEST_VIRKSOMHET_2,
                mottakere = listOf(TEST_MOTTAKER_2),
                tittel = "Sakstittel for beskjed feil mottaker",
                mottattTidspunkt = OffsetDateTime.now(),
            )
        }

        val response = engine.queryNotifikasjonerJson()

        it("response inneholder riktig data") {
            response.getTypedContent<List<BrukerAPI.Notifikasjon>>("notifikasjoner/notifikasjoner").let { notifikasjoner ->
                (notifikasjoner[0] as BrukerAPI.Notifikasjon.Oppgave).let {
                    it.id shouldBe oppgaveUtenSakOpprettet.aggregateId
                    it.sak should beNull()
                }
                (notifikasjoner[1] as BrukerAPI.Notifikasjon.Beskjed).let {
                    it.id shouldBe beskjedUtenSakOpprettet.aggregateId
                    it.sak should beNull()
                }
                (notifikasjoner[2] as BrukerAPI.Notifikasjon.Oppgave).let {
                    it.id shouldBe oppgaveMedSakOpprettet.aggregateId
                    it.sak shouldNot beNull()
                    it.sak!!.tittel shouldBe "Sakstittel for oppgave"
                }
                (notifikasjoner[3] as BrukerAPI.Notifikasjon.Beskjed).let {
                    it.id shouldBe beskjedMedSakOpprettet.aggregateId
                    it.sak shouldNot beNull()
                    it.sak!!.tittel shouldBe "Sakstittel for beskjed"
                }
                (notifikasjoner[4] as BrukerAPI.Notifikasjon.Oppgave).let {
                    it.id shouldBe oppgaveMedSakFeilMottakerOpprettet.aggregateId
                    it.sak should beNull()
                }
                (notifikasjoner[5] as BrukerAPI.Notifikasjon.Beskjed).let {
                    it.id shouldBe beskjedMedSakMedFeilMottakerOpprettet.aggregateId
                    it.sak should beNull()
                }
                notifikasjoner shouldHaveSize 6
            }
        }
    }
})

