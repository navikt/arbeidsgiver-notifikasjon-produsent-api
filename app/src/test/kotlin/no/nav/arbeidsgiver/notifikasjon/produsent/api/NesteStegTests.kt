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

    describe("Endrer nesteSteg på eksisterende sak") {
        val (produsentRepository, hendelseProdusent, engine) = setupEngine()
        val sak = engine.nySak(uuid("1").toString(),null)
        val sakID = sak.getTypedContent<UUID>("$.nySak.id")
        val idempotencyKey1 = uuid("2").toString()
        val idempotencyKey2 = uuid("3").toString()

        val nesteSteg1 = engine.endreNesteSteg(sakID, "foo", idempotencyKey1)

        it("Endrer neste steg med ny idempontency key") {
            nesteSteg1.getTypedContent<String>("$.nesteStegSak.__typename") shouldBe "NesteStegSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.nesteSteg shouldBe "foo";
        }
        it("Forsøker endre neste steg med samme idempontency key og forventer ingen endring") {
            engine.endreNesteSteg(sakID, "bar", idempotencyKey1)
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.nesteSteg shouldBe "foo";
        }
        it ("Endrere med ny idempontency key og forventer endring") {
            val nesteSteg2 = engine.endreNesteSteg(sakID, "baz", idempotencyKey2)
            nesteSteg2.getTypedContent<String>("$.nesteStegSak.__typename") shouldBe "NesteStegSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.nesteSteg shouldBe "baz";
        }
        it ("Endrer neste steg til null") {
            val nesteSteg3 = engine.endreNesteSteg(sakID, null, uuid("4").toString())
            nesteSteg3.getTypedContent<String>("$.nesteStegSak.__typename") shouldBe "NesteStegSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.nesteSteg shouldBe null;
        }
        it ("Endrer neste steg uten idempontency key") {
            val nesteSteg4 = engine.endreNesteSteg(sakID, "foo", null)
            nesteSteg4.getTypedContent<String>("$.nesteStegSak.__typename") shouldBe "NesteStegSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.nesteSteg shouldBe "foo";
        }
        it ("Endrer til null uten idempontency key") {
            val nesteSteg5 = engine.endreNesteSteg(sakID, null, null)
            nesteSteg5.getTypedContent<String>("$.nesteStegSak.__typename") shouldBe "NesteStegSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.nesteSteg shouldBe null;
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
                    tilleggsinformasjon: "Her er noe tilleggsinformasjon"
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


private fun TestApplicationEngine.endreNesteSteg(
    id: UUID,
    nesteSteg: String?,
    idempotencyKey: String?,
) =
    produsentApi(
        """
            mutation {
                nesteStegSak(
                    id: "$id"
                    ${if (nesteSteg == null) "" else "nesteSteg: \"$nesteSteg\""}
                    ${if (idempotencyKey == null) "" else "idempotencyKey: \"$idempotencyKey\""}
                ) {
                    __typename
                    ... on NesteStegSakVellykket {
                        id
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            }
        """
    )

