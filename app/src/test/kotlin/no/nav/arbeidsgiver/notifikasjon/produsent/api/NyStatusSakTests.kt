package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDateTime
import java.util.*

class NyStatusSakTests : DescribeSpec({

    describe("oppdater status") {
        val (stubbedKafkaProducer, engine) = setupEngine()
        val nySakResponse = engine.nySak()
        it("vellykket nySak") {
            nySakResponse.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        val sakId = nySakResponse.getTypedContent<UUID>("$.nySak.id")

        val r1 = engine.nyStatusSak(sakId, SaksStatus.UNDER_BEHANDLING)
        it("vellykket r1") {
            r1.getTypedContent<String>("$.nyStatusSak.__typename") shouldBe "NyStatusSakVellykket"
        }
        val id1 = r1.getTypedContent<UUID>("$.nyStatusSak.id")

        val r2 = engine.nyStatusSak(
            sakId,
            SaksStatus.UNDER_BEHANDLING,
            idempotencyKey = "key"
        )
        val r3 = engine.nyStatusSak(
            sakId,
            SaksStatus.UNDER_BEHANDLING,
            idempotencyKey = "key"
        )

        it("begge var vellykket") {
            r2.getTypedContent<String>("$.nyStatusSak.__typename") shouldBe "NyStatusSakVellykket"
            r3.getTypedContent<String>("$.nyStatusSak.__typename") shouldBe "NyStatusSakVellykket"
        }
        val id2 = r2.getTypedContent<UUID>("$.nyStatusSak.id")
        val id3 = r3.getTypedContent<UUID>("$.nyStatusSak.id")
        it("begge returnerte samme id") {
            id2 shouldBe id3
        }
        it("ingen er samme som orignale (uten idempotency key)") {
            id2 shouldNotBe id1
            id3 shouldNotBe id1
        }

        val r4 = engine.nyStatusSak(
            id = sakId,
            SaksStatus.MOTTATT,
            idempotencyKey = "key",
        )
        it("avvist siden status er annerledes") {
            r4.getTypedContent<String>("$.nyStatusSak.__typename") shouldBe "Konflikt"
        }

        val r5 = engine.nyStatusSak(
            id = sakId,
            SaksStatus.FERDIG,
            hardDelete = LocalDateTime.MAX
        )
        it("vellyket oppdatering med harddelete satt i kafka") {
            r5.getTypedContent<String>("$.nyStatusSak.__typename") shouldBe "NyStatusSakVellykket"
            val hendelse = stubbedKafkaProducer.hendelser.last()
            hendelse should beInstanceOf<HendelseModel.NyStatusSak>()
            (hendelse as HendelseModel.NyStatusSak).hardDelete shouldNotBe null
        }
    }
})

private fun DescribeSpec.setupEngine(): Pair<FakeHendelseProdusent, TestApplicationEngine> {
    val stubbedKafkaProducer = FakeHendelseProdusent()
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val engine = ktorProdusentTestServer(
        kafkaProducer = stubbedKafkaProducer,
        produsentRepository = produsentRepository,
    )
    return Pair(stubbedKafkaProducer, engine)
}

private fun TestApplicationEngine.nyStatusSak(
    id: UUID,
    status: SaksStatus,
    idempotencyKey: String? = null,
    hardDelete: LocalDateTime? = null,
): TestApplicationResponse {
    return produsentApi(
        """
            mutation {
                nyStatusSak(
                    id: "$id"
                    idempotencyKey: ${idempotencyKey?.let { "\"$it\"" }}
                    nyStatus: $status
                    ${
            hardDelete?.let {
                """
                        hardDelete: {
                            nyTid: {
                                den: "$it"
                            }
                            strategi: OVERSKRIV
                        }
                    """.trimIndent()
            } ?: ""
        }
                ) {
                    __typename
                    ... on NyStatusSakVellykket {
                        id
                    }
                }
            }
        """
    )
}


private fun TestApplicationEngine.nySak(
    status: SaksStatus = SaksStatus.MOTTATT,
) =
    produsentApi(
        """
            mutation {
                nySak(
                    virksomhetsnummer: "1"
                    merkelapp: "tag"
                    grupperingsid: "grupperingsid"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    initiellStatus: $status
                    tittel: "tittel"
                    lenke: "lenke"
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )