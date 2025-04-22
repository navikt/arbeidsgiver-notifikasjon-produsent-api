package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationKalenderavtale.KalenderavtaleTilstand.*
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.*

class KalenderavtaleTest {

    private val grupperingsid = "g42"
    private val eksternId = "heuheu"
    private val merkelapp = "tag"
    private val sakOpprettet = HendelseModel.SakOpprettet(
        virksomhetsnummer = "42",
        merkelapp = "tag",
        grupperingsid = grupperingsid,
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
    )
    private val kafkaProducer = FakeHendelseProdusent()


    @Test
    fun nyKalenderavtale() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database).also {
            runBlocking {
                it.oppdaterModellEtterHendelse(sakOpprettet)
            }
        }
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val start = LocalDateTime.now().plusDays(10)
            val slutt = LocalDateTime.now().plusDays(10).plusHours(1)
            lateinit var nyKalenderavtale: MutationKalenderavtale.NyKalenderavtaleVellykket
            with(
                client.nyKalenderavtale(
                    grupperingsid = grupperingsid,
                    merkelapp = merkelapp,
                    startTidspunkt = start.toString(),
                    sluttTidspunkt = slutt.toString(),
                    eksternId = eksternId
                )
            ) {
                // status is 200 OK
                assertEquals(HttpStatusCode.OK, status)
                // response inneholder ikke feil
                assertTrue(getGraphqlErrors().isEmpty())

                // respons inneholder forventet data
                nyKalenderavtale = getTypedContent<MutationKalenderavtale.NyKalenderavtaleVellykket>("nyKalenderavtale")

                // sends message to kafka
                with(kafkaProducer.hendelser.removeLast()) {
                    this as HendelseModel.KalenderavtaleOpprettet
                    assertEquals(nyKalenderavtale.id, notifikasjonId)
                    assertEquals(sakOpprettet.sakId, sakId)
                    assertEquals(HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER, tilstand)
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
                    assertEquals(start, startTidspunkt)
                    assertEquals(slutt, sluttTidspunkt)
                    assertEquals(
                        HendelseModel.Lokasjon(
                            postnummer = "1234",
                            poststed = "Kneika",
                            adresse = "rundt svingen og borti høgget"
                        ), lokasjon
                    )
                    assertTrue(erDigitalt)
                    hardDelete as HendelseModel.LocalDateTimeOrDuration.LocalDateTime
                    assertFalse(eksterneVarsler.isEmpty())
                    assertNotNull(påminnelse)
                    assertFalse(påminnelse!!.eksterneVarsler.isEmpty())
                }

                // updates produsent modell
                val id = nyKalenderavtale.id
                with(produsentRepository.hentNotifikasjon(id)) {
                    assertNotNull(this)
                    this as ProdusentModel.Kalenderavtale
                    assertEquals(merkelapp, merkelapp)
                    assertEquals("42", virksomhetsnummer)
                    assertEquals("hello world", tekst)
                    assertEquals(grupperingsid, grupperingsid)
                    assertEquals("https://foo.bar", lenke)
                    assertEquals(eksternId, eksternId)
                    assertEquals(ProdusentModel.Kalenderavtale.Tilstand.VENTER_SVAR_FRA_ARBEIDSGIVER, tilstand)
                    assertEquals(start, startTidspunkt)
                    assertEquals(slutt, sluttTidspunkt)
                    assertEquals(
                        ProdusentModel.Kalenderavtale.Lokasjon(
                            postnummer = "1234",
                            poststed = "Kneika",
                            adresse = "rundt svingen og borti høgget"
                        ), lokasjon
                    )
                    assertTrue(digitalt)
                    assertFalse(eksterneVarsler.isEmpty())
                    assertFalse(påminnelseEksterneVarsler.isEmpty())
                }
            }

            // oppdater Kalenderavtale
            with(
                client.oppdaterKalenderavtale(
                    id = nyKalenderavtale.id,
                    idempotenceKey = "123",
                    nyTilstand = ARBEIDSGIVER_HAR_GODTATT,
                )
            ) {
                // status is 200 OK
                assertEquals(HttpStatusCode.OK, status)

                // response inneholder ikke feil
                assertTrue(getGraphqlErrors().isEmpty())

                // respons inneholder forventet data
                val oppdaterVellykket: MutationKalenderavtale.OppdaterKalenderavtaleVellykket =
                    getTypedContent<MutationKalenderavtale.OppdaterKalenderavtaleVellykket>("oppdaterKalenderavtale")

                // sends message to kafka
                with(kafkaProducer.hendelser.removeLast()) {
                    this as HendelseModel.KalenderavtaleOppdatert
                    assertEquals(nyKalenderavtale.id, notifikasjonId)
                    assertEquals(HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_HAR_GODTATT, tilstand)
                    assertEquals("https://foo.bar", lenke)
                    assertEquals("hello world", tekst)
                    assertEquals(
                        HendelseModel.Lokasjon(
                            postnummer = "1234",
                            poststed = "Kneika",
                            adresse = "rundt svingen og borti høgget"
                        ), lokasjon
                    )
                    assertEquals(true, erDigitalt)
                    hardDelete as HendelseModel.HardDeleteUpdate
                    assertFalse(eksterneVarsler.isEmpty())
                    assertNotNull(påminnelse)
                    assertFalse(påminnelse!!.eksterneVarsler.isEmpty())
                }
                // updates produsent modell
                with(produsentRepository.hentNotifikasjon(oppdaterVellykket.id)) {
                    assertNotNull(this)
                    this as ProdusentModel.Kalenderavtale
                    assertEquals(merkelapp, merkelapp)
                    assertEquals("42", virksomhetsnummer)

                    assertEquals("hello world", tekst)
                    assertEquals(grupperingsid, grupperingsid)
                    assertEquals("https://foo.bar", lenke)
                    assertEquals(eksternId, eksternId)
                    assertEquals(ProdusentModel.Kalenderavtale.Tilstand.ARBEIDSGIVER_HAR_GODTATT, tilstand)
                    assertEquals(start, startTidspunkt)
                    assertEquals(slutt, sluttTidspunkt)
                    assertEquals(
                        ProdusentModel.Kalenderavtale.Lokasjon(
                            postnummer = "1234",
                            poststed = "Kneika",
                            adresse = "rundt svingen og borti høgget"
                        ), lokasjon
                    )
                    assertTrue(digitalt)
                    assertFalse(eksterneVarsler.isEmpty())
                    assertFalse(påminnelseEksterneVarsler.isEmpty())
                }
            }

            // samme forespørsel med samme idempotensnøkkel gir samme svar
            with(
                client.oppdaterKalenderavtale(
                    id = nyKalenderavtale.id,
                    idempotenceKey = "123",
                    AVLYST
                )
            ) {
                assertEquals(
                    "OppdaterKalenderavtaleVellykket",
                    getTypedContent<String>("oppdaterKalenderavtale/__typename")
                )
            }

            // oppdaterKalenderavtaleByEksternId
            with(
                client.oppdaterKalenderavtaleByEksternId(merkelapp, eksternId, ARBEIDSGIVER_VIL_AVLYSE)
            ) {
                // status is 200 OK
                assertEquals(HttpStatusCode.OK, status)
                // response inneholder ikke feil
                assertTrue(getGraphqlErrors().isEmpty())

                // respons inneholder forventet data
                val oppdatertByEksternId =
                    getTypedContent<MutationKalenderavtale.OppdaterKalenderavtaleVellykket>("oppdaterKalenderavtaleByEksternId")

                // sends message to kafka
                with(kafkaProducer.hendelser.removeLast()) {
                    this as HendelseModel.KalenderavtaleOppdatert
                    assertEquals(oppdatertByEksternId.id, notifikasjonId)
                    assertFalse(eksterneVarsler.isEmpty())
                    assertNotNull(påminnelse)
                    assertFalse(påminnelse!!.eksterneVarsler.isEmpty())
                }

                // updates produsent modell
                with(produsentRepository.hentNotifikasjon(oppdatertByEksternId.id)) {
                    assertNotNull(this)
                    this as ProdusentModel.Kalenderavtale
                    assertEquals(merkelapp, merkelapp)
                    assertEquals("42", virksomhetsnummer)

                    assertEquals("hello world", tekst)
                    assertEquals(grupperingsid, grupperingsid)
                    assertEquals("https://foo.bar", lenke)
                    assertEquals(eksternId, eksternId)
                    assertEquals(ProdusentModel.Kalenderavtale.Tilstand.ARBEIDSGIVER_VIL_AVLYSE, tilstand)
                    assertEquals(start, startTidspunkt)
                    assertEquals(slutt, sluttTidspunkt)
                    assertEquals(
                        ProdusentModel.Kalenderavtale.Lokasjon(
                            postnummer = "1234",
                            poststed = "Kneika",
                            adresse = "rundt svingen og borti høgget"
                        ), lokasjon
                    )
                    assertTrue(digitalt)
                    assertFalse(eksterneVarsler.isEmpty())
                    assertFalse(påminnelseEksterneVarsler.isEmpty())
                }
            }

            // samme forespørsel med samme idempotensnøkkel gir samme svar
            with(
                client.oppdaterKalenderavtaleByEksternId(
                    merkelapp = merkelapp,
                    eksternId = eksternId,
                    idempotenceKey = "321",
                    nyTilstand = AVLYST
                )
            ) {
                assertEquals(
                    "OppdaterKalenderavtaleVellykket",
                    getTypedContent<String>("oppdaterKalenderavtaleByEksternId/__typename")
                )
            }
        }
    }

    @Test
    fun `starttidspunkt etter sluttidspunkt ved opprettelse`() =
        withTestDatabase(Produsent.databaseConfig) { database ->
            val produsentRepository = ProdusentRepositoryImpl(database).also {
                runBlocking {
                    it.oppdaterModellEtterHendelse(sakOpprettet)
                }
            }
            ktorProdusentTestServer(
                kafkaProducer = kafkaProducer,
                produsentRepository = produsentRepository,
            ) {
                with(
                    client.nyKalenderavtale(
                        grupperingsid = grupperingsid,
                        merkelapp = merkelapp,
                        eksternId = "400",
                        startTidspunkt = "2024-10-12T08:20:50.52",
                        sluttTidspunkt = "2024-10-12T07:20:50.52"
                    )
                ) {
                    // status is 200 OK
                    assertEquals(HttpStatusCode.OK, status)
                    // response inneholder ikke feil
                    assertTrue(getGraphqlErrors().isEmpty())
                    // respons inneholder forventet data
                    getTypedContent<Error.UgyldigKalenderavtale>("nyKalenderavtale")
                }
            }
        }


    @Test
    fun `Validering av mottaker mot sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database).also {
            runBlocking {
                it.oppdaterModellEtterHendelse(sakOpprettet)
            }
        }
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {

            with(
                client.opprettKalenderavtaleMedMottaker(
                    grupperingsId = "g42",
                    virksomhetsnummer = "41",
                    eksternId = "1",
                    mottaker =
                        """naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    }"""
                )
            ) {
                // Kalenderavtale har feil virksomhetsnummer
                this as Error.UgyldigMottaker
            }

            with(
                client.opprettKalenderavtaleMedMottaker(
                    grupperingsId = "g42",
                    virksomhetsnummer = "42",
                    eksternId = "2",
                    mottaker =
                        """altinn: {
                        serviceCode: "1",
                        serviceEdition: "1"
                    }"""
                )
            ) {
                // Kalenderavtale har feil mottakerType
                this as Error.UgyldigMottaker
            }

        }
    }
}

private suspend fun HttpClient.nyKalenderavtale(
    grupperingsid: String,
    merkelapp: String,
    startTidspunkt: String,
    sluttTidspunkt: String,
    eksternId: String = "heu",
) = produsentApi(
    """
        mutation {
            nyKalenderavtale(
                mottakere: [{
                    naermesteLeder: {
                        naermesteLederFnr: "12345678910"
                        ansattFnr: "321"
                    } 
                }]
                lenke: "https://foo.bar"
                tekst: "hello world"
                merkelapp: "$merkelapp"
                grupperingsid: "$grupperingsid"
                eksternId: "$eksternId"
                startTidspunkt: "$startTidspunkt"
                sluttTidspunkt: "$sluttTidspunkt"
                lokasjon: {
                    postnummer: "1234"
                    poststed: "Kneika"
                    adresse: "rundt svingen og borti høgget"
                }
                erDigitalt: true
                virksomhetsnummer: "42"
                eksterneVarsler: [{
                    altinntjeneste: {
                        sendetidspunkt: {
                            sendevindu: LOEPENDE
                        }
                        mottaker: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                        innhold: "foo"
                        tittel: "bar"
                    }
                }]
                paaminnelse: {
                    tidspunkt: {
                        foerStartTidspunkt: "PT24H"
                    }
                    eksterneVarsler: [{
                        altinntjeneste: {
                            sendevindu: LOEPENDE
                            mottaker: {
                                serviceCode: "5441"
                                serviceEdition: "1"
                            }
                            innhold: "baz"
                            tittel: "buz"
                        }
                    }]
                }
                hardDelete: {
                  den: "2019-10-13T07:20:50.52"
                }
            ) {
                __typename
                ... on NyKalenderavtaleVellykket {
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
)

private suspend fun HttpClient.oppdaterKalenderavtale(
    id: UUID,
    idempotenceKey: String = "1234",
    nyTilstand: MutationKalenderavtale.KalenderavtaleTilstand,
) = produsentApi(
    """
        mutation {
            oppdaterKalenderavtale(
                id: "$id"
                idempotencyKey: "$idempotenceKey"
                nyLenke: "https://foo.bar"
                nyTekst: "hello world"
                nyTilstand: $nyTilstand
                nyLokasjon: {
                    postnummer: "1234"
                    poststed: "Kneika"
                    adresse: "rundt svingen og borti høgget"
                }
                nyErDigitalt: true
                hardDelete: {
                  nyTid: { 
                    den: "2019-10-13T07:20:50.52" 
                    }
                  strategi: OVERSKRIV
                }
                eksterneVarsler: [{
                    altinntjeneste: {
                        sendetidspunkt: {
                            sendevindu: LOEPENDE
                        }
                        mottaker: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                        innhold: "foo"
                        tittel: "bar"
                    }
                }]
                paaminnelse: {
                    tidspunkt: {
                        foerStartTidspunkt: "PT24H"
                    }
                    eksterneVarsler: [{
                        altinntjeneste: {
                            sendevindu: LOEPENDE
                            mottaker: {
                                serviceCode: "5441"
                                serviceEdition: "1"
                            }
                            innhold: "baz"
                            tittel: "buz"
                        }
                    }]
                }
            ) {
                __typename
                ... on OppdaterKalenderavtaleVellykket {
                    id
                }
                ... on Error {
                    feilmelding
                }
            }
        }
    """.trimIndent()
)

private suspend fun HttpClient.oppdaterKalenderavtaleByEksternId(
    merkelapp: String,
    eksternId: String,
    nyTilstand: MutationKalenderavtale.KalenderavtaleTilstand,
    idempotenceKey: String = "1234",
) = produsentApi(
    """
        mutation {
            oppdaterKalenderavtaleByEksternId(
                merkelapp: "$merkelapp"
                eksternId: "$eksternId"
                idempotencyKey: "$idempotenceKey"
                nyTilstand: $nyTilstand
                nyLenke: "https://foo.bar"
                nyTekst: "hello world"
                nyLokasjon: {
                    postnummer: "1234"
                    poststed: "Kneika"
                    adresse: "rundt svingen og borti høgget"
                }
                nyErDigitalt: true
                hardDelete: {
                  nyTid: { 
                    den: "2019-10-13T07:20:50.52" 
                    }
                  strategi: OVERSKRIV
                }
                eksterneVarsler: [{
                    altinntjeneste: {
                        sendetidspunkt: {
                            sendevindu: LOEPENDE
                        }
                        mottaker: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                        innhold: "foo"
                        tittel: "bar"
                    }
                }]
                paaminnelse: {
                    tidspunkt: {
                        foerStartTidspunkt: "PT24H"
                    }
                    eksterneVarsler: [{
                        altinntjeneste: {
                            sendevindu: LOEPENDE
                            mottaker: {
                                serviceCode: "5441"
                                serviceEdition: "1"
                            }
                            innhold: "baz"
                            tittel: "buz"
                        }
                    }]
                }
            ) {
                __typename
                ... on OppdaterKalenderavtaleVellykket {
                    id
                }
                ... on Error {
                    feilmelding
                }
            }
        }
    """.trimIndent()
)

private suspend fun HttpClient.opprettKalenderavtaleMedMottaker(
    grupperingsId: String,
    eksternId: String,
    mottaker: String,
    virksomhetsnummer: String,
    startTidspunkt: String = "2024-10-12T07:20:50.52",
    sluttTidspunkt: String = "2024-10-12T08:20:50.52",
): MutationKalenderavtale.NyKalenderavtaleResultat {
    val mutation =
        """
        mutation {
            nyKalenderavtale(
                mottakere: [{
                    $mottaker
                    }
                ]
               
                lenke: "https://foo.bar"
                tekst: "hello world"
                merkelapp: "tag"
                grupperingsid: "$grupperingsId"
                eksternId: "$eksternId"
                startTidspunkt: "$startTidspunkt"
                sluttTidspunkt: "$sluttTidspunkt"
                lokasjon: {
                    postnummer: "1234"
                    poststed: "Kneika"
                    adresse: "rundt svingen og borti høgget"
                }
                erDigitalt: true
                virksomhetsnummer: "$virksomhetsnummer"
                eksterneVarsler: [{
                    altinntjeneste: {
                        sendetidspunkt: {
                            sendevindu: LOEPENDE
                        }
                        mottaker: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                        innhold: "foo"
                        tittel: "bar"
                    }
                }]
                hardDelete: {
                  den: "2019-10-13T07:20:50.52"
                }
            ) {
                __typename
                ... on NyKalenderavtaleVellykket {
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
    return response.getTypedContent("nyKalenderavtale")
}