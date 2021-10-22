package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel.NærmesteLederFor
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

class NærmesteLederTilgangsstyring: DescribeSpec({

    val database = testDatabase(Bruker.databaseConfig)
    val model = BrukerModelImpl(database)

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

        val beskjed2 = beskjedOpprettet(
            eksternId = "2",
            mottaker = mottaker2,
            id = UUID.fromString("c49fb832-1d2c-4557-bc64-a4c926098572"),
        ).also { model.oppdaterModellEtterHendelse(it) }

        val beskjed3 = beskjedOpprettet(
            eksternId = "3",
            mottaker = mottaker3,
            id = UUID.fromString("c49fb832-1d2c-4557-bc64-a4c926098573"),
        ).also { model.oppdaterModellEtterHendelse(it) }

        it("ingen ansatte gir ingen notifikasjoner") {
            val notifikasjoner = model.hentNotifikasjoner(nærmesteLeder, listOf(), listOf())
            notifikasjoner should beEmpty()
        }

        it("får notifikasjon om nåværende ansatt") {
            val notifikasjoner = model.hentNotifikasjoner(nærmesteLeder, listOf(), ansatte(mottaker1))
            notifikasjoner shouldHaveSize 1
            val beskjed = notifikasjoner[0] as BrukerModel.Beskjed
            beskjed.id shouldBe beskjed1.notifikasjonId
        }

        it("""
            får ikke notifikasjon om ansatte i andre virksomheter,
            selv om du er nærmeste leder for den personen i denne virksomheten
            """.trimMargin()
        ) {
            val notifikasjoner = model.hentNotifikasjoner(
                nærmesteLeder,
                listOf(),
                ansatte(mottaker1.copy(virksomhetsnummer = virksomhet2))
            )
            notifikasjoner shouldHaveSize 1
            val beskjed = notifikasjoner[0] as BrukerModel.Beskjed
            beskjed.id shouldBe beskjed3.notifikasjonId
        }
    }
})

fun ansatte(vararg mottakere: Mottaker): List<NærmesteLederFor> {
    return mottakere.toList()
        .filterIsInstance<NærmesteLederMottaker>()
        .map {
            NærmesteLederFor(
                virksomhetsnummer = it.virksomhetsnummer,
                ansattFnr = it.ansattFnr
            )
        }
}

fun beskjedOpprettet(mottaker: Mottaker, id: UUID, eksternId: String) = Hendelse.BeskjedOpprettet(
    virksomhetsnummer = mottaker.virksomhetsnummer,
    merkelapp = "",
    eksternId = eksternId,
    mottaker = mottaker,
    hendelseId = id,
    notifikasjonId = id,
    tekst = "test",
    lenke = "https://nav.no",
    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
    kildeAppNavn = "",
    produsentId = "",
)
