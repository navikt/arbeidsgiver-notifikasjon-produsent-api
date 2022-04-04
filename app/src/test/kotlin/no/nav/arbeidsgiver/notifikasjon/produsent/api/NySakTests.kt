package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class NySakTests: DescribeSpec({
    val embeddedKafka = embeddedKafka()

    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = embeddedKafka.newProducer(),
            produsentRepository = produsentRepository,
        )
    )

    describe("opprett nySak") {
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
    }
})

private fun TestApplicationEngine.nySak(
    grupperingsid: String = "1",
    merkelapp: String = "tag",
    status: SaksStatus = SaksStatus.MOTTATT,
    tittel: String = "tittel",
    lenke: String = "lenke",
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
                    initiell_status: $status
                    tidspunkt: "2020-01-01T01:01Z"
                    tittel: "$tittel"
                    lenke: "$lenke"
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )