package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

class HentNotifikasjonTests : DescribeSpec({

    val virksomhetsnummer = "123"
    val merkelapp = "tag"
    val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    val grupperingsid = "sak1"

    val uuid = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()
    val oppgave = HendelseModel.OppgaveOpprettet(
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
    val beskjed = HendelseModel.BeskjedOpprettet(
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


    describe("hentNotifikasjon") {
        context("notifikasjon finnes ikke") {
            val (_, engine) = setupEngine()
            it("respons inneholder forventet data") {
                engine.produsentApi(
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

        context("produsent mangler tilgang til merkelapp") {
            val (produsentModel, engine) = setupEngine()
            produsentModel.oppdaterModellEtterHendelse(oppgave.copy(merkelapp = "fubar"))

            it("respons inneholder forventet data") {
                engine.produsentApi(
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

        context("henter notifikasjon på id") {
            val (produsentModel, engine) = setupEngine()
            produsentModel.oppdaterModellEtterHendelse(oppgave)
            produsentModel.oppdaterModellEtterHendelse(beskjed)

            withData(listOf(oppgave.notifikasjonId, beskjed.notifikasjonId)) { notifikasjonId ->
                engine.produsentApi(
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
})

private fun DescribeSpec.setupEngine(): Pair<ProdusentRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val engine = ktorProdusentTestServer(produsentRepository = produsentModel)
    return Pair(produsentModel, engine)
}

