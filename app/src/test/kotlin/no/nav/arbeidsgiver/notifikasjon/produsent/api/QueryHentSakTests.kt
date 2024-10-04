package no.nav.arbeidsgiver.notifikasjon.produsent.api;

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*;
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.UUID;


class QueryHentSakTests : DescribeSpec({
    describe("hentSak query returnerer felter som stemmer") {
        val engine = setupEngine()

        val sak1 = engine.nySak(
            grupperingsid = "grupperingsid1",
            merkelapp = "tag",
            tilleggsinformasjon = "Tilleggsinformasjon1"
        )

        it ("Tilleggsinformasjon felter returneres med id") {
            val idSak1 = sak1.getTypedContent<UUID>("$.nySak.id")
            val hentetSak = engine.hentSak(idSak1)

            hentetSak.getTypedContent<String>("$.hentSak.sak.tilleggsinformasjon") shouldBe "Tilleggsinformasjon1"
        }

        it ("Tilleggsinformasjon returneres med grupperingsid") {
            val hentetSak = engine.hentSakByGrupperingsid("grupperingsid1", "tag")

            hentetSak.getTypedContent<String>("$.hentSakMedGrupperingsid.sak.tilleggsinformasjon") shouldBe "Tilleggsinformasjon1"
        }

    }
})

private fun DescribeSpec.setupEngine(): TestApplicationEngine {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val stubbedKafkaProducer = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = stubbedKafkaProducer,
        produsentRepository = produsentRepository,
    )
    return  engine
}


private fun TestApplicationEngine.hentSak(
    id: UUID
) = produsentApi(
    """
    query {
        hentSak(
            id: "${id}"
        ) {
                    __typename
                    ... on HentetSak {
                        sak {
                            id
                            tilleggsinformasjon
                        }
                    }
                }
    }
""".trimIndent()
)

private fun TestApplicationEngine.hentSakByGrupperingsid(
    grupperingsid: String,
    merkelapp: String
) = produsentApi(
    """
    query {
        hentSakMedGrupperingsid(
            grupperingsid: "$grupperingsid"
            merkelapp: "$merkelapp"
        ) {
                    __typename
                    ... on HentetSak {
                        sak {
                            id
                            tilleggsinformasjon
                        }
                    }
                }
    }
""".trimIndent()
)

private fun TestApplicationEngine.nySak(
    grupperingsid: String = "1",
    merkelapp: String = "tag",
    status: SaksStatus = SaksStatus.MOTTATT,
    tittel: String = "tittel",
    lenke: String? = "lenke",
    tilleggsinformasjon: String? = "her er noe tilleggsinformasjon",
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
                    tilleggsinformasjon: "$tilleggsinformasjon"
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