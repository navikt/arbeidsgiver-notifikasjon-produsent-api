package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class NySakTest {

    @Test
    fun `opprett nySak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val response1 = client.nySak()
            // should be successfull
            assertEquals("NySakVellykket", response1.getTypedContent<String>("$.nySak.__typename"))
            val id1 = response1.getTypedContent<UUID>("$.nySak.id")

            val response2 = client.nySak()
            // opprett enda en sak should be successfull
            assertEquals("NySakVellykket", response2.getTypedContent<String>("$.nySak.__typename"))
            val id2 = response2.getTypedContent<UUID>("$.nySak.id")

            // samme id
            assertEquals(id2, id1)

            val response3 = client.nySak(
                grupperingsid = "2"
            )
            // opprett enda en sak should be successfull
            assertEquals("NySakVellykket", response3.getTypedContent<String>("$.nySak.__typename"))

            val id3 = response3.getTypedContent<UUID>("$.nySak.id")

            // different id
            assertNotEquals(id3, id1)

            val response4 = client.nySak(
                lenke = "annerledes lenke"
            )

            // opprett duplikat sak should fail
            assertEquals("DuplikatGrupperingsid", response4.getTypedContent<String>("$.nySak.__typename"))

            val response5 = client.nySak(
                tittel = "annerledes tittel"
            )
            // opprett duplikat sak should fail
            assertEquals("DuplikatGrupperingsid", response5.getTypedContent<String>("$.nySak.__typename"))

            val response6 = client.nySak(
                merkelapp = "tag2"
            )
            // should be successfull
            assertEquals("NySakVellykket", response6.getTypedContent<String>("$.nySak.__typename"))
            val id6 = response6.getTypedContent<UUID>("$.nySak.id")

            // should be different
            assertFalse(listOf(id1, id2, id3).contains(id6))

            val response7 = client.nySak(
                status = SaksStatus.UNDER_BEHANDLING
            )
            // should be fail because status is different
            assertEquals("DuplikatGrupperingsid", response7.getTypedContent<String>("$.nySak.__typename"))

            val response8 = client.nySak(
                grupperingsid = "3",
                hardDeleteDen = "2020-01-01T01:01"
            )
            // should be successful
            assertEquals("NySakVellykket", response8.getTypedContent<String>("$.nySak.__typename"))
            // should have hard delete in kafka message
            with(
                stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.SakOpprettet>()
                    .last()
            ) {
                this.hardDelete as HendelseModel.LocalDateTimeOrDuration.LocalDateTime
            }

            val response9 = client.nySak(
                grupperingsid = "4",
                hardDeleteOm = "P2YT1M",
            )
            // should be successful
            assertEquals("NySakVellykket", response9.getTypedContent<String>("$.nySak.__typename"))
            // should have hard delete in kafka message
            with(
                stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.SakOpprettet>()
                    .last()
            ) {
                this.hardDelete as HendelseModel.LocalDateTimeOrDuration.Duration
                val duration = (this.hardDelete as HendelseModel.LocalDateTimeOrDuration.Duration).value
                assertEquals(2, duration.get(ChronoUnit.YEARS))
                assertEquals(60, duration.get(ChronoUnit.SECONDS))
            }


            val response10 = client.nySak(
                grupperingsid = "5",
                hardDeleteOm = "P2YT",
            )
            // should be successful
            assertEquals("NySakVellykket", response10.getTypedContent<String>("$.nySak.__typename"))
            // should have hard delete in kafka message
            with(
                stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.SakOpprettet>()
                    .last()
            ) {
                this.hardDelete as HendelseModel.LocalDateTimeOrDuration.Duration
                val duration = (this.hardDelete as HendelseModel.LocalDateTimeOrDuration.Duration).value
                assertEquals(2, duration.get(ChronoUnit.YEARS))
                assertEquals(0, duration.get(ChronoUnit.SECONDS))
            }

            val response11 = client.nySak(
                grupperingsid = "6",
                lenke = null,
            )
            // should be successful
            assertEquals("NySakVellykket", response11.getTypedContent<String>("$.nySak.__typename"))

            client.nySak(
                grupperingsid = "13",
                tilleggsinformasjon = "Dette er tilleggsinfo"
            )

            // tilleggsinformasjon should be set
            val hendelse = stubbedKafkaProducer.hendelser
                .filterIsInstance<HendelseModel.SakOpprettet>().first { it.grupperingsid == "13" }
            assertEquals("Dette er tilleggsinfo", hendelse.tilleggsinformasjon)
        }
    }
}

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