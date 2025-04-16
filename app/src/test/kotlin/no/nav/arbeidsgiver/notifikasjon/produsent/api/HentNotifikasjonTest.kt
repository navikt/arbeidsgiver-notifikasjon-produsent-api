package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test

class HentNotifikasjonTest {

    private val virksomhetsnummer = "123"
    private val merkelapp = "tag"
    private val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    private val grupperingsid = "sak1"

    private val uuid = UUID.randomUUID()
    private val uuid2 = UUID.randomUUID()
    private val oppgave = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        eksternId = "1",
        mottakere = listOf(mottaker),
        hendelseId = uuid,
        notifikasjonId = uuid,
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
        grupperingsid = grupperingsid,
        kildeAppNavn = "",
        produsentId = "",
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )
    private val beskjed = HendelseModel.BeskjedOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        eksternId = "2",
        mottakere = listOf(mottaker),
        hendelseId = uuid2,
        notifikasjonId = uuid2,
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
        grupperingsid = grupperingsid,
        kildeAppNavn = "",
        produsentId = "",
        eksterneVarsler = listOf(),
        hardDelete = null,
        sakId = null,
    )


    @Test
    fun `hentNotifikasjon notifikasjon finnes ikke`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            client.produsentApi(
                """
                        query {
                            hentNotifikasjon(id: "${UUID.randomUUID()}") {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
            ).getTypedContent<Error.NotifikasjonFinnesIkke>("hentNotifikasjon")
        }
    }

    @Test
    fun `hentNotifikasjon produsent mangler tilgang til merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = produsentRepository
        ) {
            produsentRepository.oppdaterModellEtterHendelse(oppgave.copy(merkelapp = "fubar"))

            client.produsentApi(
                """
                        query {
                            hentNotifikasjon(id: "${oppgave.notifikasjonId}") {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
            ).getTypedContent<Error.UgyldigMerkelapp>("hentNotifikasjon")
        }
    }

    @Test
    fun `hentNotifikasjon henter notifikasjon på id`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            produsentRepository = produsentRepository
        ) {
            produsentRepository.oppdaterModellEtterHendelse(oppgave)
            produsentRepository.oppdaterModellEtterHendelse(beskjed)

            listOf(oppgave.notifikasjonId, beskjed.notifikasjonId).forEach { notifikasjonId ->
                client.produsentApi(
                    """
                    query {
                        hentNotifikasjon(id: "$notifikasjonId") {
                            __typename
                            ... on HentetNotifikasjon {
                                notifikasjon {
                                    __typename
                                    ... on Beskjed {
                                        mottakere {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid
                                        }
                                        beskjed {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                    }
                                    ... on Oppgave {
                                        mottakere {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid
                                        }
                                        oppgave {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                            tilstand
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                    }
                                }
                            }
                        }
                    }
                    """.trimIndent()
                ).getTypedContent<QueryNotifikasjoner.HentetNotifikasjon>("hentNotifikasjon")
            }
        }
    }
}

