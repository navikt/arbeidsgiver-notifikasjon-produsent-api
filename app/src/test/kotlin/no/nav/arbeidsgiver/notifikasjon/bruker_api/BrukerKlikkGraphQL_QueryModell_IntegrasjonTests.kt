package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

class BrukerKlikkGraphQL_QueryModell_IntegrasjonTests: DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)
    val nærmesteLederModel = NærmesteLederModelImpl(database)

    val fnr = "00000000000"
    val ansattFnr = "12344321"
    val virksomhetsnummer = "1234"
    val mottaker = NærmesteLederMottaker(fnr, ansattFnr, virksomhetsnummer)

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
    )

    describe("Brukerklikk-oppførsel") {
        val uuid = UUID.fromString("c39986f2-b31a-11eb-8529-0242ac130003")

        val beskjedOpprettet = BeskjedOpprettet(
            virksomhetsnummer = virksomhetsnummer,
            mottakere = listOf(mottaker),
            opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
            hendelseId = uuid,
            notifikasjonId = uuid,
            merkelapp = "",
            eksternId = "",
            tekst = "",
            lenke = "",
            kildeAppNavn = "",
            produsentId = "",
            grupperingsid = null,
            eksterneVarsler = listOf(),
        )
        queryModel.oppdaterModellEtterHendelse(beskjedOpprettet)
        nærmesteLederModel.oppdaterModell(NærmesteLederModel.NarmesteLederLeesah(
            narmesteLederId = uuid,
            fnr = mottaker.ansattFnr,
            narmesteLederFnr = mottaker.naermesteLederFnr,
            orgnummer = mottaker.virksomhetsnummer,
            aktivTom = null
        ))

        /* sjekk at beskjed ikke er klikket på */
        val response = engine.brukerApi(
            """
            {
                notifikasjoner{
                    notifikasjoner {
                        ...on Beskjed {
                            brukerKlikk {
                                klikketPaa
                            }
                        }
                    }
                }
            }
            """
        )

        val klikkMarkørFørKlikk = response.getTypedContent<Boolean>("$.notifikasjoner.notifikasjoner[0].brukerKlikk.klikketPaa")

        it("notifikasjon er ikke klikket på") {
            klikkMarkørFørKlikk shouldBe false
        }


        val brukerKlikket = BrukerKlikket(
            virksomhetsnummer = virksomhetsnummer,
            fnr = fnr,
            hendelseId = UUID.randomUUID(),
            notifikasjonId = uuid,
            kildeAppNavn = "",
            produsentId = null,
        )

        queryModel.oppdaterModellEtterHendelse(brukerKlikket)

        /* sjekk at beskjed ikke er klikket på */
        val responseEtterKlikk = engine.brukerApi(
            """
            {
                notifikasjoner{
                    notifikasjoner {
                        ...on Beskjed {
                            brukerKlikk {
                                klikketPaa
                            }
                        }
                    }
                }
            }
            """
        )
        val klikkMarkørEtterKlikk = responseEtterKlikk.getTypedContent<Boolean>("$.notifikasjoner.notifikasjoner[0].brukerKlikk.klikketPaa")

        it("notifikasjon er klikket på") {
            klikkMarkørEtterKlikk shouldBe true
        }
    }
})
