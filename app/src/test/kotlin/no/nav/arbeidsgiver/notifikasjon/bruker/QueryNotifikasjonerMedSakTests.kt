package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.OffsetDateTime

class QueryNotifikasjonerMedSakTests : DescribeSpec({

    context("Query.notifikasjoner med sak") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(TEST_TILGANG_1, TEST_TILGANG_2)
                )
            }
        )
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
                merkelapp = it.merkelapp,
                mottakere = it.mottakere,
                tittel = "Sakstittel for oppgave",
                mottattTidspunkt = OffsetDateTime.now(),
                tilleggsinformasjon = "Tilleggsinformasjon om saken"

            )
        }
        
        val beskjedMedSakOpprettet = brukerRepository.beskjedOpprettet(
            mottakere = listOf(TEST_MOTTAKER_2),
            grupperingsid = "3",
            opprettetTidspunkt = opprettetTidspunkt.minusHours(3),
            tekst = "beskjed med sak",
        ).also {
            brukerRepository.sakOpprettet(
                grupperingsid = it.grupperingsid!!,
                merkelapp = it.merkelapp,
                mottakere = it.mottakere,
                tittel = "Sakstittel for beskjed",
                mottattTidspunkt = OffsetDateTime.now(),
            )
        }

        val kalenderavtaleMedSak = brukerRepository.sakOpprettet(
            mottakere = listOf(TEST_MOTTAKER_2),
            grupperingsid = "4",
            tittel = "Sakstittel for kalenderavtale",
            mottattTidspunkt = OffsetDateTime.now(),

        ).let { sak ->
            brukerRepository.kalenderavtaleOpprettet(
                opprettetTidspunkt = opprettetTidspunkt.minusHours(4),
                grupperingsid = sak.grupperingsid,
                mottakere = sak.mottakere,
                merkelapp = sak.merkelapp,
                tekst = "kalenderavtale med sak",
                sakId = sak.sakId,
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
                    it.sak!!.tilleggsinformasjon shouldBe "Tilleggsinformasjon om saken"
                }
                (notifikasjoner[3] as BrukerAPI.Notifikasjon.Beskjed).let {
                    it.id shouldBe beskjedMedSakOpprettet.aggregateId
                    it.sak shouldNot beNull()
                    it.sak!!.tittel shouldBe "Sakstittel for beskjed"
                }
                (notifikasjoner[4] as BrukerAPI.Notifikasjon.Kalenderavtale).let {
                    it.id shouldBe kalenderavtaleMedSak.aggregateId
                    it.sorteringTidspunkt.toInstant() shouldBe kalenderavtaleMedSak.opprettetTidspunkt.toInstant()
                    it.sak shouldNot beNull()
                    it.sak!!.tittel shouldBe "Sakstittel for kalenderavtale"
                }
                notifikasjoner shouldHaveSize 5
            }
        }
    }
})

