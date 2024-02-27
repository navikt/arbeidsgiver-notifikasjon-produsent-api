package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.util.*

class BrukerRepositoryNærmesteLederTests : DescribeSpec({


    describe("BrukerRepository NærmesteLeder") {
        context("når mottar slett hendelse uten at det er noe å slette") {
            val database = testDatabase(Bruker.databaseConfig)
            val brukerRepository = BrukerRepositoryImpl(database)
            val narmesteLederFnr = "42"
            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003"),
                    fnr = "43",
                    narmesteLederFnr = narmesteLederFnr,
                    orgnummer = "44",
                    aktivTom = LocalDate.now()
                )
            )

            it("har ingen kobling i databasen") {
                database.hentAnsatte(narmesteLederFnr) shouldBe emptyList()
            }
        }

        context("når mottar to forskjellige koblinger") {
            val database = testDatabase(Bruker.databaseConfig)
            val brukerRepository = BrukerRepositoryImpl(database)
            val narmesteLederId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = narmesteLederId,
                    fnr = "1",
                    narmesteLederFnr = "1",
                    orgnummer = "1",
                    aktivTom = null
                )
            )
            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = narmesteLederId,
                    fnr = "2",
                    narmesteLederFnr = "2",
                    orgnummer = "2",
                    aktivTom = null
                )
            )

            it("den siste mottatte koblingen blir gjeldende") {
                database.hentAnsatte("1") shouldBe emptyList()
                database.hentAnsatte("2") shouldBe listOf(
                    NærmesteLederFor(
                        ansattFnr = "2",
                        virksomhetsnummer = "2"
                    )
                )
            }
        }
    }
})


data class NærmesteLederFor(
    val ansattFnr: String,
    val virksomhetsnummer: String,
)
private suspend fun Database.hentAnsatte(narmesteLederFnr: String): List<NærmesteLederFor> {
    return nonTransactionalExecuteQuery(
        """
            select * from naermeste_leder_kobling 
                where naermeste_leder_fnr = ?
            """, {
            text(narmesteLederFnr)
        }) {
        NærmesteLederFor(
            ansattFnr = getString("fnr"),
            virksomhetsnummer = getString("orgnummer"),
        )
    }
}
