package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.temporal.ChronoUnit
import java.util.*

class NySakTests : DescribeSpec({

    describe("opprett nySak") {
        val (stubbedKafkaProducer, engine) = setupEngine()
        val response1 = engine.nySak()
        it("should be successfull") {
            response1.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        val id1 = response1.getTypedContent<UUID>("$.nySak.id")

        val response2 = engine.nySak()
        it("opprett enda en sak should be successfull") {
            response2.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        val id2 = response2.getTypedContent<UUID>("$.nySak.id")

        it("samme id") {
            id1 shouldBe id2
        }

        val response3 = engine.nySak(
            grupperingsid = "2"
        )
        it("opprett enda en sak should be successfull") {
            response3.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }

        val id3 = response3.getTypedContent<UUID>("$.nySak.id")

        it("different id") {
            id3 shouldNotBe id1
        }

        val response4 = engine.nySak(
            lenke = "annerledes lenke"
        )

        it("opprett duplikat sak should fail") {
            response4.getTypedContent<String>("$.nySak.__typename") shouldBe "DuplikatGrupperingsid"
        }

        val response5 = engine.nySak(
            tittel = "annerledes tittel"
        )
        it("opprett duplikat sak should fail") {
            response5.getTypedContent<String>("$.nySak.__typename") shouldBe "DuplikatGrupperingsid"
        }

        val response6 = engine.nySak(
            merkelapp = "tag2"
        )
        it("should be successfull") {
            response6.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        val id6 = response6.getTypedContent<UUID>("$.nySak.id")

        it("should be different") {
            listOf(id1, id2, id3) shouldNotContain id6
        }

        val response7 = engine.nySak(
            status = SaksStatus.UNDER_BEHANDLING
        )
        it("should be fail because status is different") {
            response7.getTypedContent<String>("$.nySak.__typename") shouldBe "DuplikatGrupperingsid"
        }

        val response8 = engine.nySak(
            grupperingsid = "3",
            hardDeleteDen = "2020-01-01T01:01"
        )
        it("should be successful") {
            response8.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        it("should have hard delete in kafka message") {
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<HendelseModel.SakOpprettet>()
                .last()
            hendelse.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
        }

        val response9 = engine.nySak(
            grupperingsid = "4",
            hardDeleteOm = "P2YT1M",
        )
        it("should be successful") {
            response9.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        it("should have hard delete in kafka message") {
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<HendelseModel.SakOpprettet>()
                .last()
            val hardDelete = hendelse.hardDelete
            hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.Duration::class)
            val duration = (hardDelete as HendelseModel.LocalDateTimeOrDuration.Duration).value
            duration.get(ChronoUnit.YEARS) shouldBe 2
            duration.get(ChronoUnit.SECONDS) shouldBe 60
        }

        val response10 = engine.nySak(
            grupperingsid = "5",
            hardDeleteOm = "P2YT",
        )
        it("should be successful") {
            response10.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        it("should have hard delete in kafka message") {
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<HendelseModel.SakOpprettet>()
                .last()
            val hardDelete = hendelse.hardDelete
            hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.Duration::class)
            val duration = (hardDelete as HendelseModel.LocalDateTimeOrDuration.Duration).value
            duration.get(ChronoUnit.YEARS) shouldBe 2
            duration.get(ChronoUnit.SECONDS) shouldBe 0
        }

        val response11 = engine.nySak(
            grupperingsid = "6",
            lenke = null,
        )
        it("should be successful") {
            response11.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
    }
})

private fun DescribeSpec.setupEngine(): Pair<FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val stubbedKafkaProducer = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = stubbedKafkaProducer,
        produsentRepository = produsentRepository,
    )
    return Pair(stubbedKafkaProducer, engine)
}

private fun TestApplicationEngine.nySak(
    grupperingsid: String = "1",
    merkelapp: String = "tag",
    status: SaksStatus = SaksStatus.MOTTATT,
    tittel: String = "tittel",
    lenke: String? = "lenke",
    hardDeleteDen: String? = null,
    hardDeleteOm: String? = null,
) =
    produsentApi(
        """
            mutation {
                nySak(
                    virksomhetsnummer: "1"
                    merkelapp: "$merkelapp"
                    grupperingsid: "$grupperingsid"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    initiellStatus: $status
                    tidspunkt: "2020-01-01T01:01Z"
                    tittel: "$tittel"
                    ${lenke?.let { """ lenke: "$it" """ } ?: ""}
                    ${
            hardDeleteDen?.let {
                """
                        |hardDelete: {
                        |  den: "$it"
                        |}""".trimMargin()
            } ?: ""
        }
                    ${
            hardDeleteOm?.let {
                """
                        |hardDelete: {
                        |  om: "$it"
                        |}""".trimMargin()
            } ?: ""
        }
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )