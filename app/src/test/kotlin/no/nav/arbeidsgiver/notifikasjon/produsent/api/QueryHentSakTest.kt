package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryHentSakTest {

    @Test
    fun `hentSak query returnerer felter som stemmer`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val sak1 = client.nySak(
                grupperingsid = "grupperingsid1",
                merkelapp = "tag",
                tilleggsinformasjon = "Tilleggsinformasjon1"
            )

            // Tilleggsinformasjon felter returneres med id
            val idSak1 = sak1.getTypedContent<UUID>("$.nySak.id")
            with(client.hentSak(idSak1)) {
                assertEquals("Tilleggsinformasjon1", getTypedContent<String>("$.hentSak.sak.tilleggsinformasjon"))
            }

            // Tilleggsinformasjon returneres med grupperingsid
            with(client.hentSakByGrupperingsid("grupperingsid1", "tag")) {
                assertEquals(
                    "Tilleggsinformasjon1",
                    getTypedContent<String>("$.hentSakMedGrupperingsid.sak.tilleggsinformasjon")
                )
            }
        }
    }
}


private suspend fun HttpClient.hentSak(
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

private suspend fun HttpClient.hentSakByGrupperingsid(
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

private suspend fun HttpClient.nySak(
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