package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime

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

        val mottaker1 = database.nonTransactionalExecuteQuery(
            """
                    select * from mottaker_altinn_enkeltrettighet
                    where notifikasjon_id = ?
                """,
            setup = {
                uuid(uuid("0"))
            },
            transform = {
                AltinnMottaker(
                    virksomhetsnummer = getString("virksomhet"),
                    serviceCode = getString("service_code"),
                    serviceEdition = getString("service_edition"),
                )

            })
            .single()

        val mottaker2 = database.nonTransactionalExecuteQuery(
            """
                    select * from mottaker_altinn_enkeltrettighet
                    where notifikasjon_id = ?
                """,
            setup = {
                uuid(uuid("1"))
            },
            transform = {
                AltinnMottaker(
                    virksomhetsnummer = getString("virksomhet"),
                    serviceCode = getString("service_code"),
                    serviceEdition = getString("service_edition"),
                )
            })
            .single()

        val mottaker3 = database.nonTransactionalExecuteQuery(
            """
                    select * from mottaker_digisyfo
                    where notifikasjon_id = ?
                """,
            setup = {
                uuid(uuid("2"))
            },
            transform = {
                NærmesteLederMottaker(
                    virksomhetsnummer = getString("virksomhet"),
                    naermesteLederFnr = getString("fnr_leder"),
                    ansattFnr = getString("fnr_sykmeldt"),
                )
            })
            .single()


        val mottaker4 = database.nonTransactionalExecuteQuery(
            """
                    select * from mottaker_digisyfo
                    where notifikasjon_id = ?
                """,
            setup = {
                uuid(uuid("3"))
            },
            transform = {
                NærmesteLederMottaker(
                    virksomhetsnummer = getString("virksomhet"),
                    naermesteLederFnr = getString("fnr_leder"),
                    ansattFnr = getString("fnr_sykmeldt"),
                )
            })
            .single()


        it("the first should be migrated") {
            mottaker1.virksomhetsnummer shouldBe "1"
            mottaker1.serviceCode shouldBe "2"
            mottaker1.serviceEdition shouldBe "3"
        }

        it("the second should be migrated") {
            mottaker2.virksomhetsnummer shouldBe "4"
            mottaker2.serviceCode shouldBe "5"
            mottaker2.serviceEdition shouldBe "6"
        }

        it("the third should be migrated") {
            mottaker3.virksomhetsnummer shouldBe "7"
            mottaker3.ansattFnr shouldBe "8"
            mottaker3.naermesteLederFnr shouldBe "9"
        }
        it("the fourth should be migrated") {
            mottaker4.virksomhetsnummer shouldBe "10"
            mottaker4.ansattFnr shouldBe "11"
            mottaker4.naermesteLederFnr shouldBe "12"
        }
    }
})