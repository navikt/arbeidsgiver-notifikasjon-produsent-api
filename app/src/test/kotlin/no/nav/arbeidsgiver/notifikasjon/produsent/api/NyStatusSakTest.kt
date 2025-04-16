package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class NyStatusSakTest {

    @Test
    fun `oppdater status`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val nySakResponse = client.nySak()
            // vellykket nySak
            assertEquals("NySakVellykket", nySakResponse.getTypedContent<String>("$.nySak.__typename"))
            val sakId = nySakResponse.getTypedContent<UUID>("$.nySak.id")

            val r1 = client.nyStatusSak(sakId, SaksStatus.UNDER_BEHANDLING)
            // vellykket r1
            assertEquals("NyStatusSakVellykket", r1.getTypedContent<String>("$.nyStatusSak.__typename"))
            val id1 = r1.getTypedContent<UUID>("$.nyStatusSak.id")

            val r2 = client.nyStatusSak(
                sakId,
                SaksStatus.UNDER_BEHANDLING,
                idempotencyKey = "key"
            )
            val r3 = client.nyStatusSak(
                sakId,
                SaksStatus.UNDER_BEHANDLING,
                idempotencyKey = "key"
            )

            // begge var vellykket
            assertEquals("NyStatusSakVellykket", r2.getTypedContent<String>("$.nyStatusSak.__typename"))
            assertEquals("NyStatusSakVellykket", r3.getTypedContent<String>("$.nyStatusSak.__typename"))
            val id2 = r2.getTypedContent<UUID>("$.nyStatusSak.id")
            val id3 = r3.getTypedContent<UUID>("$.nyStatusSak.id")
            // begge returnerte samme id
            assertEquals(id3, id2)
            // ingen er samme som orignale (uten idempotency key)
            assertNotEquals(id2, id1)
            assertNotEquals(id3, id1)

            val r4 = client.nyStatusSak(
                id = sakId,
                SaksStatus.MOTTATT,
                idempotencyKey = "key",
            )
            // avvist siden status er annerledes
            assertEquals("Konflikt", r4.getTypedContent<String>("$.nyStatusSak.__typename"))

            val r5 = client.nyStatusSakByGrupperingsid(
                grupperingsid = "grupperingsid",
                merkelapp = "tag",
                SaksStatus.FERDIG,
            )
            // vellyket oppdatering ved grupperingsid
            assertEquals("NyStatusSakVellykket", r5.getTypedContent<String>("$.nyStatusSakByGrupperingsid.__typename"))

            val r6 = client.nyStatusSak(
                id = sakId,
                SaksStatus.FERDIG,
                hardDelete = LocalDateTime.MAX.minusDays(1)
            )
            // vellyket oppdatering med harddelete satt i kafka
            assertEquals("NyStatusSakVellykket", r6.getTypedContent<String>("$.nyStatusSak.__typename"))
            with(stubbedKafkaProducer.hendelser.last()) {
                this as HendelseModel.NyStatusSak
                assertNotNull(hardDelete)
                assertEquals(LocalDateTime.MAX.minusDays(1), hardDelete!!.nyTid.denOrNull())
            }

            val r7 = client.nyStatusSak(
                id = sakId,
                SaksStatus.FERDIG,
                hardDelete = LocalDateTime.MAX
            )
            // vellyket oppdatering med harddelete satt i kafka
            assertEquals("NyStatusSakVellykket", r7.getTypedContent<String>("$.nyStatusSak.__typename"))
            with(stubbedKafkaProducer.hendelser.last()) {
                this as HendelseModel.NyStatusSak
                assertNotNull(hardDelete)
                assertEquals(LocalDateTime.MAX, hardDelete!!.nyTid.denOrNull())
            }
        }
    }
}

private suspend fun HttpClient.nyStatusSak(
    id: UUID,
    status: SaksStatus,
    idempotencyKey: String? = null,
    hardDelete: LocalDateTime? = null,
) = produsentApi(
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
                    statuser {
                        overstyrStatusTekstMed
                        status
                        tidspunkt
                    }
                }
            }
        }
    """
)

private suspend fun HttpClient.nyStatusSakByGrupperingsid(
    grupperingsid: String,
    merkelapp: String,
    status: SaksStatus,
    idempotencyKey: String? = null,
    hardDelete: LocalDateTime? = null,
) = produsentApi(
    """
        mutation {
            nyStatusSakByGrupperingsid(
                grupperingsid: "$grupperingsid"
                merkelapp: "${merkelapp}"
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
                    statuser {
                        overstyrStatusTekstMed
                        status
                        tidspunkt
                    }
                }
            }
        }
    """
)


private suspend fun HttpClient.nySak(
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
                    tilleggsinformasjon: "tilleggsinformasjon"
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