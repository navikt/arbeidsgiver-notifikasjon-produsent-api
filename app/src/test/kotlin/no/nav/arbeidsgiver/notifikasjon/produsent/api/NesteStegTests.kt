package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*



class NesteStegTests: DescribeSpec({
    describe("Oppretter ny sak med neste steg") {
        val (produsentRepository, hendelseProdusent, engine) = setupEngine()
        val sakUtenNesteSteg = engine.nySak(uuid("1").toString())
        val sakMedNesteSteg = engine.nySak(uuid("2").toString(),"foo")

        val sakUtenNesteStegID = sakUtenNesteSteg.getTypedContent<UUID>("$.nySak.id")
        val sakMedNesteStegID = sakMedNesteSteg.getTypedContent<UUID>("$.nySak.id")

        it("should be successfull") {
            sakUtenNesteSteg.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
            sakMedNesteSteg.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }

        it("should not have neste steg") {
            val sak = produsentRepository.hentSak(sakUtenNesteStegID)!!
            sak.nesteSteg shouldBe null;
        }

        it("should have neste steg") {
            val sak = produsentRepository.hentSak(sakMedNesteStegID)!!
            sak.nesteSteg shouldBe "foo";
        }
    }
})

private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val hendelseProdusent = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = hendelseProdusent,
        produsentRepository = produsentRepository,
    )
    return Triple(produsentRepository, hendelseProdusent, engine)
}



private fun TestApplicationEngine.nySak(
    grupperingsid: String,
    nesteSteg : String? = null,
) =
    produsentApi(
        """
            mutation {
                nySak(
                    virksomhetsnummer: "1"
                    merkelapp: "tag"
                    grupperingsid: "$grupperingsid"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    initiellStatus: ${SaksStatus.MOTTATT}
                    tittel: "Foo"
                    ${if (nesteSteg == null) "" else "nesteSteg: \"$nesteSteg\""}
                    lenke: null
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )
