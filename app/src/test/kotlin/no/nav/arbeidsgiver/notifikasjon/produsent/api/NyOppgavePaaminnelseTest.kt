package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NyOppgavePaaminnelseTest {

    @Test
    fun `oppgave med frist konkret tidspunkt for påminnelse`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2019-11-01T00:00:00Z",
                        """
                    frist: "2020-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2019-12-01T00:00:00"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettes uten feil
                getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            }

            with(stubbedKafkaProducer.hendelser.first()) {
                this as HendelseModel.OppgaveOpprettet
                assertNotNull(påminnelse)
            }
        }
    }

    @Test
    fun `oppgave uten frist konkret tidspunkt for påminnelse`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2019-11-01T00:00:00Z",
                        """
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2020-01-02T00:00:00"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettes uten feil
                getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            }

            with(stubbedKafkaProducer.hendelser.first()) {
                this as HendelseModel.OppgaveOpprettet
                assertNotNull(påminnelse)
            }
        }
    }

    @Test
    fun `oppgave med frist konkret tidspunkt for påminnelse etter frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2019-11-01T00:00:00Z",
                        """
                    frist: "2020-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2020-01-02T00:00:00"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettelse feiler med feilmelding
                getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
            }
        }
    }

    @Test
    fun `oppgave uten frist konkret tidspunkt for påminnelse før opprettelse`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-02T00:00:00Z",
                        """
                    paaminnelse: {
                        tidspunkt: {
                            konkret: "2020-01-01T00:00:00"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettelse feiler med feilmelding
                getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
            }
        }
    }


    @Test
    fun `oppgave uten frist, tidspunkt for påminnelse relativ til opprettelse`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    paaminnelse: {
                        tidspunkt: {
                            etterOpprettelse: "P2DT3H4M"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettes uten feil
                getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            }
            with(stubbedKafkaProducer.hendelser.first()) {
                this as HendelseModel.OppgaveOpprettet
                assertNotNull(påminnelse)
            }
        }
    }

    @Test
    fun `oppgave uten frist, tidspunkt for påminnelse relativ til frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettelse feiler med feilmelding
                getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
            }
        }
    }

    @Test
    fun `oppgave med frist, tidspunkt for påminnelse relativ til frist`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettes uten feil
                getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            }
            with(stubbedKafkaProducer.hendelser.first()) {
                this as HendelseModel.OppgaveOpprettet
                assertNotNull(påminnelse)
            }
        }
    }

    @Test
    fun `oppgave med frist, tidspunkt for påminnelse relativ til frist blir før opprettelse`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2020-01-02"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                    }
                """,
                    )
                )
            ) {
                // opprettelse feiler med feilmelding
                getTypedContent<Error.UgyldigPåminnelseTidspunkt>("nyOppgave")
            }
        }
    }

    @Test
    fun `ekstern varlser med tom liste`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: []
                    }
                """,
                    )
                )
            ) {
                // opprettelse uten varsler
                getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
            }
            with(stubbedKafkaProducer.hendelser[0]) {
                this as HendelseModel.OppgaveOpprettet
                assertNotNull(påminnelse)
                assertTrue(påminnelse!!.eksterneVarsler.isEmpty())
            }
        }
    }

    @Test
    fun `ekstern varlser med 1 sms`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                sms: {
                                    mottaker: {
                                        kontaktinfo: {
                                            tlf: "99999999"
                                        }
                                    }
                                    smsTekst: "hei"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
                    )
                )
            ) {
                // opprettelse ok, med en sms
                val r = getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")

                val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
                assertEquals(1, hendelse.påminnelse?.eksterneVarsler?.size)
                val varsel = hendelse.påminnelse!!.eksterneVarsler[0]
                assertEquals(
                    HendelseModel.SmsVarselKontaktinfo(
                        varselId = r.paaminnelse!!.eksterneVarsler[0].id,
                        tlfnr = "99999999",
                        fnrEllerOrgnr = "0",
                        smsTekst = "hei",
                        sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                        sendeTidspunkt = null,
                    ), varsel
                )
            }
        }
    }

    @Test
    fun `ekstern varlser med 1 epost`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                epost: {
                                    mottaker: {
                                        kontaktinfo: {
                                            epostadresse: "1234@1234.no"
                                        }
                                    }
                                    epostTittel: "hei"
                                    epostHtmlBody: "body"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
                    )
                )
            ) {
                // opprettelse ok, med en epost
                val r = getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
                val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
                assertEquals(1, hendelse.påminnelse?.eksterneVarsler?.size)
                val varsel = hendelse.påminnelse!!.eksterneVarsler[0]
                assertEquals(
                    HendelseModel.EpostVarselKontaktinfo(
                        varselId = r.paaminnelse!!.eksterneVarsler[0].id,
                        epostAddr = "1234@1234.no",
                        fnrEllerOrgnr = "0",
                        tittel = "hei",
                        htmlBody = "body",
                        sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                        sendeTidspunkt = null,
                    ), varsel
                )
            }
        }
    }

    @Test
    fun `ekstern varlser med epost og sms`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                epost: {
                                    mottaker: {
                                        kontaktinfo: {
                                            epostadresse: "1234@1234.no"
                                        }
                                    }
                                    epostTittel: "hei"
                                    epostHtmlBody: "body"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                            {
                                sms: {
                                    mottaker: {
                                        kontaktinfo: {
                                            tlf: "99999999"
                                        }
                                    }
                                    smsTekst: "hei"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
                    )
                )
            ) {
                // opprettelse ok, med sms og epost
                val varsler = getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
                    .paaminnelse!!
                    .eksterneVarsler
                val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
                assertEquals(2, hendelse.påminnelse?.eksterneVarsler?.size)

                assertEquals(
                    setOf(
                        HendelseModel.EpostVarselKontaktinfo(
                            varselId = varsler[0].id,
                            epostAddr = "1234@1234.no",
                            fnrEllerOrgnr = "0",
                            tittel = "hei",
                            htmlBody = "body",
                            sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        ),
                        HendelseModel.SmsVarselKontaktinfo(
                            varselId = varsler[1].id,
                            tlfnr = "99999999",
                            fnrEllerOrgnr = "0",
                            smsTekst = "hei",
                            sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        ),
                    ),
                    hendelse.påminnelse!!.eksterneVarsler.toSet()
                )
            }
        }
    }

    @Test
    fun `ekstern varlser med 1 tjeneste`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                altinntjeneste: {
                                    mottaker: {
                                        serviceCode: "1234"
                                        serviceEdition: "1"
                                    }
                                    tittel: "hei"
                                    innhold: "body"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
                    )
                )
            ) {
                // opprettelse ok, med en tjeneste
                val r = getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
                val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
                assertEquals(1, hendelse.påminnelse?.eksterneVarsler?.size)
                val varsel = hendelse.påminnelse!!.eksterneVarsler[0]
                assertEquals(
                    HendelseModel.AltinntjenesteVarselKontaktinfo(
                        varselId = r.paaminnelse!!.eksterneVarsler[0].id,
                        virksomhetsnummer = "0",
                        serviceCode = "1234",
                        serviceEdition = "1",
                        tittel = "hei. ",
                        innhold = "body",
                        sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                        sendeTidspunkt = null,
                    ),
                    varsel
                )
            }
        }
    }

    @Test
    fun `ekstern varlser med 1 ressurs`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                altinnressurs: {
                                    mottaker: {
                                        ressursId: "nav_foo"
                                    }
                                    epostTittel: "hei"
                                    epostHtmlBody: "body"
                                    smsTekst: "tazte priv?"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
                    )
                )
            ) {
                // opprettelse ok, med en ressurs
                val r = getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
                val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
                assertEquals(1, hendelse.påminnelse?.eksterneVarsler?.size)
                val varsel = hendelse.påminnelse!!.eksterneVarsler[0]
                assertEquals(
                    HendelseModel.AltinnressursVarselKontaktinfo(
                        varselId = r.paaminnelse!!.eksterneVarsler[0].id,
                        virksomhetsnummer = "0",
                        ressursId = "nav_foo",
                        epostTittel = "hei. ",
                        epostHtmlBody = "body",
                        smsTekst = "tazte priv?",
                        sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                        sendeTidspunkt = null,
                    ), varsel
                )
            }
        }
    }

    @Test
    fun `ekstern varlser med epost og sms og tjeneste og ressurs`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val stubbedKafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = stubbedKafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            with(
                client.produsentApi(
                    nyOppgave(
                        "2020-01-01T00:00:00Z",
                        """
                    frist: "2021-01-01"
                    paaminnelse: {
                        tidspunkt: {
                            foerFrist: "P2DT3H4M"
                        }
                        eksterneVarsler: [
                            { 
                                epost: {
                                    mottaker: {
                                        kontaktinfo: {
                                            epostadresse: "1234@1234.no"
                                        }
                                    }
                                    epostTittel: "hei"
                                    epostHtmlBody: "body"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                            {
                                sms: {
                                    mottaker: {
                                        kontaktinfo: {
                                            tlf: "99999999"
                                        }
                                    }
                                    smsTekst: "hei"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                            { 
                                altinntjeneste: {
                                    mottaker: {
                                        serviceCode: "1234"
                                        serviceEdition: "1"
                                    }
                                    tittel: "hei"
                                    innhold: "body"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                            { 
                                altinnressurs: {
                                    mottaker: {
                                        ressursId: "nav_foo"
                                    }
                                    epostTittel: "hei"
                                    epostHtmlBody: "body"
                                    smsTekst: "tazte priv?"
                                    sendevindu: NKS_AAPNINGSTID
                                }
                            }
                        ]
                    }
                """,
                    )
                )
            ) {

                // opprettelse ok, med sms og epost
                val varsler = getTypedContent<MutationNyOppgave.NyOppgaveVellykket>("nyOppgave")
                    .paaminnelse!!
                    .eksterneVarsler
                val hendelse = (stubbedKafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet)
                assertEquals(4, hendelse.påminnelse?.eksterneVarsler?.size)
                assertEquals(
                    setOf(
                        HendelseModel.EpostVarselKontaktinfo(
                            varselId = varsler[0].id,
                            epostAddr = "1234@1234.no",
                            fnrEllerOrgnr = "0",
                            tittel = "hei",
                            htmlBody = "body",
                            sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        ),
                        HendelseModel.SmsVarselKontaktinfo(
                            varselId = varsler[1].id,
                            tlfnr = "99999999",
                            fnrEllerOrgnr = "0",
                            smsTekst = "hei",
                            sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        ),
                        HendelseModel.AltinntjenesteVarselKontaktinfo(
                            varselId = varsler[2].id,
                            virksomhetsnummer = "0",
                            serviceCode = "1234",
                            serviceEdition = "1",
                            tittel = "hei. ",
                            innhold = "body",
                            sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        ),
                        HendelseModel.AltinnressursVarselKontaktinfo(
                            varselId = varsler[3].id,
                            virksomhetsnummer = "0",
                            ressursId = "nav_foo",
                            epostTittel = "hei. ",
                            epostHtmlBody = "body",
                            smsTekst = "tazte priv?",
                            sendevindu = HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        )
                    ),
                    hendelse.påminnelse!!.eksterneVarsler.toSet()
                )
            }
        }
    }
}

private fun nyOppgave(opprettetTidspunkt: String, fragment: String) = """
            mutation {
                nyOppgave(nyOppgave: {
                    $fragment
                    mottaker: {
                        naermesteLeder: {
                            naermesteLederFnr: "12345678910",
                            ansattFnr: "321"
                        } 
                    }
                    metadata: {
                        eksternId: "0"
                        virksomhetsnummer: "0"
                        opprettetTidspunkt: "$opprettetTidspunkt"
                    }
                    notifikasjon: {
                        lenke: ""
                        tekst: ""
                        merkelapp: "tag"
                    }
                }) {
                    __typename
                    ... on NyOppgaveVellykket {
                        id
                        eksterneVarsler {
                            id
                        }
                        paaminnelse {
                            eksterneVarsler {
                                id
                            }
                        }
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            
            }
        """