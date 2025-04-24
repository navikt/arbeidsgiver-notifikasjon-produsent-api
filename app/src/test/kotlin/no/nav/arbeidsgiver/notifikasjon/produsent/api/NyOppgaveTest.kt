package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NyOppgaveTest {

    @Test
    fun `produsent-api happy path`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        val kafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val nyOppgave = client.opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>()

            // sends message to kafka
            with(kafkaProducer.hendelser.removeLast()) {
                this as OppgaveOpprettet
                assertEquals(nyOppgave.id, notifikasjonId)
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
                assertEquals(null, frist)
            }

            // updates produsent modell
            assertNotNull(produsentRepository.hentNotifikasjon(nyOppgave.id))

            val nyOppgave2 = client.opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>()
            // idempotent oppførsel ved opprettelse av identisk sak
            assertEquals(nyOppgave.id, nyOppgave2.id)
        }
    }

    @Test
    fun `produsent-api happy path med frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        val kafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val nyOppgave =
                client.opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(frist = """frist: "2020-01-02"  """)

            // sends message to kafka
            with(kafkaProducer.hendelser.removeLast()) {
                this as OppgaveOpprettet
                assertEquals(nyOppgave.id, notifikasjonId)
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
                assertEquals(LocalDate.parse("2020-01-02"), frist)
            }

            // updates produsent modell
            assertNotNull(produsentRepository.hentNotifikasjon(nyOppgave.id))

            val nyOppgave2 =
                client.opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(frist = """frist: "2020-01-02"  """)
            // idempotent oppførsel ved opprettelse av identisk sak
            assertEquals(nyOppgave.id, nyOppgave2.id)

            client.opprettOgTestNyOppgave<Error.DuplikatEksternIdOgMerkelapp>(frist = """frist: "2020-01-01"  """)
        }
    }

    @Test
    fun `produsent-api happy path med grupperingsid for sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        val kafkaProducer = FakeHendelseProdusent()
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
            val nyOppgave = client.opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(
                grupperingsid = """grupperingsid: "g42"  """
            )

            // sends message to kafka
            with(kafkaProducer.hendelser.removeLast()) {
                this as OppgaveOpprettet
                assertEquals(nyOppgave.id, notifikasjonId)
                assertEquals("https://foo.bar", lenke)
                assertEquals("hello world", tekst)
                assertEquals(sakOpprettet.grupperingsid, grupperingsid)
                assertEquals(sakOpprettet.sakId, sakId)
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
            assertNotNull(produsentRepository.hentNotifikasjon(nyOppgave.id))
        }
    }

    @Test
    fun `Validering av mottaker mot sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        val kafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            HendelseModel.SakOpprettet(
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

            val nyOppgave = client.opprettOppgaveMedMottaker(
                grupperingsId = "g42",
                virksomhetsnummer = "41",
                eksternId = "1",
                mottaker =
                    """naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    }"""
            )
            // Oppgave har feil virksomhetsnummer
            nyOppgave as Error.UgyldigMottaker

            val nyOppgave2 = client.opprettOppgaveMedMottaker(
                grupperingsId = "g42",
                virksomhetsnummer = "42",
                eksternId = "2",
                mottaker =
                    """altinn: {
                        serviceCode: "1",
                        serviceEdition: "1"
                    }"""
            )

            // Oppgave har feil mottakerType
            nyOppgave2 as Error.UgyldigMottaker

            val nyOppgave3 = client.opprettOppgaveMedMottaker(
                grupperingsId = "g41",
                virksomhetsnummer = "41",
                eksternId = "3",
                mottaker =
                    """altinn: {
                        serviceCode: "1",
                        serviceEdition: "1"
                    }"""
            )

            // Oppgave har ikke grupperingsid, og er ikke koblet til sak
            nyOppgave3 as MutationNyOppgave.NyOppgaveVellykket
        }
    }
}

private suspend inline fun <reified T : MutationNyOppgave.NyOppgaveResultat> HttpClient.opprettOgTestNyOppgave(
    frist: String = "",
    grupperingsid: String = "",

    ): T {
    val response = produsentApi(
        """
        mutation {
            nyOppgave(nyOppgave: {
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
                $frist
            }) {
                __typename
                ... on NyOppgaveVellykket {
                    id
                    eksterneVarsler {
                        id
                    }
                }
                ... on DuplikatEksternIdOgMerkelapp {
                    feilmelding
                    idTilEksisterende
                }
                ... on UgyldigMottaker {
                    feilmelding
                }
            }
        }
    """.trimIndent()
    )
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.getGraphqlErrors().isEmpty())

    val nyOppgave = response.getTypedContent<MutationNyOppgave.NyOppgaveResultat>("nyOppgave")
    return nyOppgave as T
}

private suspend fun HttpClient.opprettOppgaveMedMottaker(
    grupperingsId: String,
    eksternId: String,
    mottaker: String,
    virksomhetsnummer: String,
): MutationNyOppgave.NyOppgaveResultat {
    val mutation =
        """
        mutation {
            nyOppgave(nyOppgave: {
                mottakere: [{
                    $mottaker
                    }
                ]
                
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
                ... on NyOppgaveVellykket {
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
    return response.getTypedContent("nyOppgave")
}