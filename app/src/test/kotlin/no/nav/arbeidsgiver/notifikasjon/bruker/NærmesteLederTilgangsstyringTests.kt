package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.virksomhetsnummer
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid

class NærmesteLederTilgangsstyringTests: DescribeSpec({

    suspend fun BrukerRepository.beskjedOpprettet(mottaker: Mottaker) = beskjedOpprettet(
        virksomhetsnummer = mottaker.virksomhetsnummer,
        mottakere = listOf(mottaker),
    )

    describe("Tilgangsstyring av nærmeste leder") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)

        val virksomhet1 = "1".repeat(9)
        val virksomhet2 = "2".repeat(9)
        val nærmesteLeder = "1".repeat(11)
        val annenNærmesteLeder = "2".repeat(11)
        val ansatt1 = "3".repeat(11)

        val mottaker1 = NærmesteLederMottaker(
            naermesteLederFnr = nærmesteLeder,
            ansattFnr = ansatt1,
            virksomhetsnummer = virksomhet1,
        )

        val mottaker2 = NærmesteLederMottaker(
            naermesteLederFnr = annenNærmesteLeder,
            ansattFnr = ansatt1,
            virksomhetsnummer = virksomhet1,
        )

        val mottaker3 = NærmesteLederMottaker(
            naermesteLederFnr = nærmesteLeder,
            ansattFnr = ansatt1,
            virksomhetsnummer = virksomhet2,
        )

        val beskjed1 = brukerRepository.beskjedOpprettet(mottaker = mottaker1)
        brukerRepository.beskjedOpprettet(mottaker = mottaker2)
        val beskjed3 = brukerRepository.beskjedOpprettet(mottaker = mottaker3)

        it("ingen ansatte gir ingen notifikasjoner") {
            val notifikasjoner = brukerRepository.hentNotifikasjoner(nærmesteLeder, Tilganger.EMPTY)
            notifikasjoner should beEmpty()
        }

        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("12"),
                fnr = mottaker1.ansattFnr,
                narmesteLederFnr = mottaker1.naermesteLederFnr,
                orgnummer = mottaker1.virksomhetsnummer,
                aktivTom = null,
            )
        )
        it("får notifikasjon om nåværende ansatt") {
            val notifikasjoner = brukerRepository.hentNotifikasjoner(nærmesteLeder, Tilganger.EMPTY)
            notifikasjoner shouldHaveSize 1
            val beskjed = notifikasjoner[0] as BrukerModel.Beskjed
            beskjed.id shouldBe beskjed1.notifikasjonId
        }

        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("13"),
                fnr = mottaker1.ansattFnr,
                narmesteLederFnr = mottaker1.naermesteLederFnr,
                orgnummer = virksomhet2,
                aktivTom = null,
            )
        )

        it("""
            får ikke notifikasjon om ansatte i andre virksomheter,
            selv om du er nærmeste leder for den personen i denne virksomheten
            """.trimMargin()
        ) {
            val notifikasjoner = brukerRepository.hentNotifikasjoner(
                nærmesteLeder,
                Tilganger.EMPTY,
                //ansatte(mottaker1.copy(virksomhetsnummer = virksomhet2))
            )
            notifikasjoner shouldHaveSize 2
            val b1 = notifikasjoner[0] as BrukerModel.Beskjed
            listOf(beskjed1.notifikasjonId, beskjed3.notifikasjonId) shouldContain b1.id
            val b2 = notifikasjoner[0] as BrukerModel.Beskjed
            listOf(beskjed1.notifikasjonId, beskjed3.notifikasjonId) shouldContain b2.id
        }
    }
})

