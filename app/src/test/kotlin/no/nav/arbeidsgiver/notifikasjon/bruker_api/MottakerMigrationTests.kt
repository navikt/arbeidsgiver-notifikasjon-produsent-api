package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class MottakerMigrationTests: DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val model = BrukerModelImpl(database)

    describe("uflyttede mottakere") {
        database.nonTransactionalExecuteBatch("""
            insert into notifikasjon
            (type, tilstand, id, merkelapp, tekst, lenke, ekstern_id, mottaker, opprettet_tidspunkt)
            values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
            listOf(
                uuid("0") to AltinnMottaker(virksomhetsnummer = "1", serviceCode = "2", serviceEdition = "3"),
                uuid("1") to AltinnMottaker(virksomhetsnummer = "4", serviceCode = "5", serviceEdition = "6"),
                uuid("2") to NærmesteLederMottaker(virksomhetsnummer = "7", ansattFnr = "8", naermesteLederFnr = "9"),
                uuid("3") to NærmesteLederMottaker(virksomhetsnummer = "10", ansattFnr = "11", naermesteLederFnr = "12")
            )
        ) { (id, mottaker) ->
            string("type")
            string("ok")
            uuid(id)
            string("merkelapp")
            string("tekst")
            string("lenke")
            string("ekstern_id")
            jsonb(mottaker)
            timestamptz(OffsetDateTime.parse("2020-01-01T01:01+10"))
        }

        model.startBackgroundMottakerMigration()

        suspend fun hentVnr(id: UUID) =
            database.nonTransactionalExecuteQuery(
                """
                    select virksomhetsnummer from notifikasjon where id = ?
                """,
                setup = { uuid(id) },
                transform = { getString("virksomhetsnummer") }
            )
                .single()

        val mottaker1 = hentVnr(uuid("0"))
        val mottaker2 = hentVnr(uuid("1"))
        val mottaker3 = hentVnr(uuid("2"))
        val mottaker4 = hentVnr(uuid("3"))

        it("the first should be migrated") {
            mottaker1 shouldBe "1"
        }

        it("the second should be migrated") {
            mottaker2 shouldBe "4"
        }

        it("the third should be migrated") {
            mottaker3 shouldBe "7"
        }
        it("the fourth should be migrated") {
            mottaker4 shouldBe "10"
        }
    }
})