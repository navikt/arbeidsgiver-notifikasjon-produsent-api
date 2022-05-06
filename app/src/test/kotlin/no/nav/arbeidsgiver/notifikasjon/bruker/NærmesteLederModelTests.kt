package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.util.*

class NærmesteLederModelTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val model = NærmesteLederModelImpl(database)

    describe("NærmesteLederModel") {
        context("når mottar slett hendelse uten at det er noe å slette") {
            val narmesteLederFnr = "42"
            model.oppdaterModell(NærmesteLederModel.NarmesteLederLeesah(
                narmesteLederId =  UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003"),
                fnr = "43",
                narmesteLederFnr = narmesteLederFnr,
                orgnummer = "44",
                aktivTom = LocalDate.now()
            ))

            it("har ingen kobling i databasen") {
                model.hentAnsatte(narmesteLederFnr) shouldBe emptyList()
            }
        }

        context("når mottar to forskjellige koblinger") {
            val narmesteLederId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
            model.oppdaterModell(NærmesteLederModel.NarmesteLederLeesah(
                narmesteLederId = narmesteLederId,
                fnr = "1",
                narmesteLederFnr = "1",
                orgnummer = "1",
                aktivTom = null
            ))
            model.oppdaterModell(NærmesteLederModel.NarmesteLederLeesah(
                narmesteLederId = narmesteLederId,
                fnr = "2",
                narmesteLederFnr = "2",
                orgnummer = "2",
                aktivTom = null
            ))

            it("den siste mottatte koblingen blir gjeldende") {
                model.hentAnsatte("1") shouldBe emptyList()
                model.hentAnsatte("2") shouldBe listOf(
                    NærmesteLederModel.NærmesteLederFor(
                        ansattFnr = "2",
                        virksomhetsnummer = "2"
                    )
                )
            }
        }
    }
})
