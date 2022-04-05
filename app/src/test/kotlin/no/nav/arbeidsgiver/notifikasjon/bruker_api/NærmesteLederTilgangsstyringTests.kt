package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.DoNotParallelize
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.virksomhetsnummer
import java.time.OffsetDateTime
import java.util.*

@DoNotParallelize
class NærmesteLederTilgangsstyringTests: DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val model = BrukerRepositoryImpl(database)
    val nærmesteLederModel = NærmesteLederModelImpl(database)

    describe("Tilgangsstyring av nærmeste leder") {

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

        val beskjed1 = beskjedOpprettet(
            eksternId = "1",
            mottaker = mottaker1,
            id = UUID.fromString("c49fb832-1d2c-4557-bc64-a4c926098571"),
        ).also { model.oppdaterModellEtterHendelse(it) }

        model.oppdaterModellEtterHendelse(
            beskjedOpprettet(
                eksternId = "2",
                mottaker = mottaker2,
                id = UUID.fromString("c49fb832-1d2c-4557-bc64-a4c926098572"),
            )
        )

        val beskjed3 = beskjedOpprettet(
            eksternId = "3",
            mottaker = mottaker3,
            id = UUID.fromString("c49fb832-1d2c-4557-bc64-a4c926098573"),
        ).also { model.oppdaterModellEtterHendelse(it) }

        it("ingen ansatte gir ingen notifikasjoner") {
            val notifikasjoner = model.hentNotifikasjoner(nærmesteLeder, Tilganger.EMPTY)
            notifikasjoner should beEmpty()
        }

        nærmesteLederModel.oppdaterModell(
            NærmesteLederModel.NarmesteLederLeesah(
                narmesteLederId = uuid("12"),
                fnr = mottaker1.ansattFnr,
                narmesteLederFnr = mottaker1.naermesteLederFnr,
                orgnummer = mottaker1.virksomhetsnummer,
                aktivTom = null,
            )
        )
        it("får notifikasjon om nåværende ansatt") {
            val notifikasjoner = model.hentNotifikasjoner(nærmesteLeder, Tilganger.EMPTY)
            notifikasjoner shouldHaveSize 1
            val beskjed = notifikasjoner[0] as BrukerModel.Beskjed
            beskjed.id shouldBe beskjed1.notifikasjonId
        }

        nærmesteLederModel.oppdaterModell(
            NærmesteLederModel.NarmesteLederLeesah(
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
            val notifikasjoner = model.hentNotifikasjoner(
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

fun beskjedOpprettet(mottaker: Mottaker, id: UUID, eksternId: String) = BeskjedOpprettet(
    virksomhetsnummer = mottaker.virksomhetsnummer,
    merkelapp = "",
    eksternId = eksternId,
    mottakere = listOf(mottaker),
    hendelseId = id,
    notifikasjonId = id,
    tekst = "test",
    lenke = "https://nav.no",
    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
    kildeAppNavn = "",
    produsentId = "",
    grupperingsid = null,
    eksterneVarsler = listOf(),
)
