package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.api.SaksStatus
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class NyStatusSakTests: DescribeSpec({
    val embeddedKafka = embeddedKafka()

    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = embeddedKafka.newProducer(),
            produsentRepository = produsentRepository,
        )
    )

    describe("oppdater status") {
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
    }
})

private fun TestApplicationEngine.nyStatusSak(
    id: UUID,
    status: SaksStatus,
    idempotencyKey: String? = null,
) =
    produsentApi(
        """
            mutation {
                nyStatusSak(
                    id: "$id"
                    status: {
                        status: {
                            status: $status
                        }
                        ${ if (idempotencyKey != null)
                                """idempotencyKey: "$idempotencyKey" """
                            else
                                ""
                        }
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


private fun TestApplicationEngine.nySak(
    status: SaksStatus = SaksStatus.MOTTATT,
) =
    produsentApi(
        """
            mutation {
                nySak(sak: {
                    virksomhetsnummer: "1"
                    merkelapp: "tag"
                    grupperingsid: "grupperingsid"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    status: {
                        status: $status
                    }
                    tittel: "tittel"
                    lenke: "lenke"
                }) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )