package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NyBeskjedTest {

    @Test
    fun `produsent-api happy path`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val nyBeskjed = client.opprettOgTestNyBeskjed()

            // sends message to kafka
            with(kafkaProducer.hendelser.removeLast()) {
                this as BeskjedOpprettet
                assertEquals(nyBeskjed.id, notifikasjonId)
                assertEquals("https://foo.bar", lenke)
                assertEquals("hello world", tekst)
                assertEquals("tag", merkelapp)
                assertEquals(
                    NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    ), mottakere.single()
                )
                assertEquals(OffsetDateTime.parse("2019-10-12T07:20:50.52Z"), opprettetTidspunkt)
                hardDelete as HendelseModel.LocalDateTimeOrDuration.LocalDateTime
            }

            // updates produsent modell
            assertNotNull(produsentRepository.hentNotifikasjon(nyBeskjed.id))
        }
    }

    @Test
    fun `produsent-api happy path med grupperingsid for sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val sakOpprettet = HendelseModel.SakOpprettet(
                virksomhetsnummer = "1",
                merkelapp = "tag",
                grupperingsid = "g42",
                mottakere = listOf(
                    NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                ),
                hendelseId = uuid("11"),
                sakId = uuid("11"),
                tittel = "test",
                lenke = "https://nav.no",
                oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                mottattTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                kildeAppNavn = "",
                produsentId = "",
                nesteSteg = null,
                hardDelete = null,
                tilleggsinformasjon = null
            ).also {
                produsentRepository.oppdaterModellEtterHendelse(it)
            }
            val nyBeskjed = client.opprettOgTestNyBeskjed(""" grupperingsid: "g42" """)

            // sends message to kafka
            with(kafkaProducer.hendelser.removeLast()) {
                this as BeskjedOpprettet
                assertEquals(nyBeskjed.id, notifikasjonId)
                assertEquals(sakOpprettet.sakId, sakId)
                assertEquals(sakOpprettet.grupperingsid, grupperingsid)
                assertEquals("https://foo.bar", lenke)
                assertEquals("hello world", tekst)
                assertEquals("tag", merkelapp)
                assertEquals(
                    NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    ), mottakere.single()
                )
                assertEquals(OffsetDateTime.parse("2019-10-12T07:20:50.52Z"), opprettetTidspunkt)
                hardDelete as HendelseModel.LocalDateTimeOrDuration.LocalDateTime
            }

            // updates produsent modell
            assertNotNull(produsentRepository.hentNotifikasjon(nyBeskjed.id))
        }
    }

    @Test
    fun `Validering av mottaker mot sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentRepository,
        ) {
            val sakOpprettet = HendelseModel.SakOpprettet(
                virksomhetsnummer = "42",
                merkelapp = "tag",
                grupperingsid = "g42",
                mottakere = listOf(
                    NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                ),
                hendelseId = uuid("11"),
                sakId = uuid("11"),
                tittel = "test",
                lenke = "https://nav.no",
                oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                mottattTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                kildeAppNavn = "",
                produsentId = "",
                nesteSteg = null,
                hardDelete = null,
                tilleggsinformasjon = null
            ).also {
                produsentRepository.oppdaterModellEtterHendelse(it)
            }

            val nyBeskjed = client.opprettBeskjedMedMottaker(
                grupperingsId = "g42",
                virksomhetsnummer = "41",
                eksternId = "1",
                mottaker =
                    """naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    }"""
            )
            // Beskjed har feil virksomhetsnummer
            nyBeskjed as Error.UgyldigMottaker

            val nyBeskjed2 = client.opprettBeskjedMedMottaker(
                grupperingsId = "g42",
                virksomhetsnummer = "42",
                eksternId = "2",
                mottaker =
                    """altinn: {
                        serviceCode: "1",
                        serviceEdition: "1"
                    }"""
            )

            // Beskjed har feil mottakerType
            nyBeskjed2 as Error.UgyldigMottaker

            val nyBeskjed3 = client.opprettBeskjedMedMottaker(
                grupperingsId = "g41",
                virksomhetsnummer = "41",
                eksternId = "3",
                mottaker =
                    """altinn: {
                        serviceCode: "1",
                        serviceEdition: "1"
                    }"""
            )

            // Beskjed har ikke grupperingsid, og er ikke koblet til sak
            nyBeskjed3 as MutationNyBeskjed.NyBeskjedVellykket
        }
    }
}


private suspend inline fun HttpClient.opprettOgTestNyBeskjed(
    grupperingsid: String = "",
): MutationNyBeskjed.NyBeskjedVellykket {
    val response = produsentApi(
        """
        mutation {
            nyBeskjed(nyBeskjed: {
                mottaker: {
                    naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    } 
                }
                notifikasjon: {
                    lenke: "https://foo.bar",
                    tekst: "hello world",
                    merkelapp: "tag",
                }
                metadata: {
                    eksternId: "heu",
                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                    virksomhetsnummer: "42"
                    hardDelete: {
                      den: "2019-10-13T07:20:50.52"
                    }
                    $grupperingsid
                }
            }) {
                __typename
                ... on NyBeskjedVellykket {
                    id
                    eksterneVarsler {
                        id
                    }
                }
            }
        }
    """.trimIndent()
    )
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.getGraphqlErrors().isEmpty())

    return response.getTypedContent<MutationNyBeskjed.NyBeskjedVellykket>("nyBeskjed")
}


private suspend fun HttpClient.opprettBeskjedMedMottaker(
    grupperingsId: String,
    eksternId: String,
    mottaker: String,
    virksomhetsnummer: String,
): MutationNyBeskjed.NyBeskjedResultat {
    val mutation =
        """
        mutation {
            nyBeskjed(nyBeskjed: {
                mottakere: [{
                    $mottaker
                }]
                
                notifikasjon: {
                    lenke: "https://foo.bar",
                    tekst: "hello world",
                    merkelapp: "tag",
                }
                metadata: {
                    eksternId: "$eksternId",
                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                    virksomhetsnummer: "$virksomhetsnummer"
                    hardDelete: {
                      den: "2019-10-13T07:20:50.52"
                    }
                    grupperingsid: "$grupperingsId",
                }
            }) {
                __typename
                ... on NyBeskjedVellykket {
                    id
                    eksterneVarsler {
                        id
                    }
                }
                ... on Error {
                    feilmelding
                }
            }
        }
    """.trimIndent()

    val response = produsentApi(mutation)
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.getGraphqlErrors().isEmpty())
    return response.getTypedContent("nyBeskjed")
}