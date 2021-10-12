package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*

class BrukerKlikkGraphQL_QueryModell_IntegrasjonTests: DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerModelImpl(database)

    val fnr = "00000000000"
    val ansattFnr = "12344321"
    val virksomhetsnummer = "1234"
    val mottaker = NærmesteLederMottaker(fnr, ansattFnr, virksomhetsnummer)

    val engine = ktorBrukerTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = AltinnStub(),
            enhetsregisteret = EnhetsregisteretStub(),
            brukerModel = queryModel,
            kafkaProducer = mockk(),
            nærmesteLederService = NærmesteLederServiceStub(mottaker)
        )
    )

    describe("Brukerklikk-oppførsel") {
        val uuid = UUID.fromString("c39986f2-b31a-11eb-8529-0242ac130003")

        val beskjedOpprettet = Hendelse.BeskjedOpprettet(
            virksomhetsnummer = virksomhetsnummer,
            mottaker = mottaker,
            opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
            hendelseId = uuid,
            notifikasjonId = uuid,
            merkelapp = "",
            eksternId = "",
            tekst = "",
            lenke = "",
            kildeAppNavn = "",
            produsentId = "",
        )
        queryModel.oppdaterModellEtterHendelse(beskjedOpprettet)

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

        val klikkMarkørFørKlikk = response.getTypedContent<Boolean>("/notifikasjoner/notifikasjoner/0/brukerKlikk/klikketPaa")

        it("notifikasjon er ikke klikket på") {
            klikkMarkørFørKlikk shouldBe false
        }


        val brukerKlikket = Hendelse.BrukerKlikket(
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
        val klikkMarkørEtterKlikk = responseEtterKlikk.getTypedContent<Boolean>("/notifikasjoner/notifikasjoner/0/brukerKlikk/klikketPaa")

        it("notifikasjon er klikket på") {
            klikkMarkørEtterKlikk shouldBe true
        }
    }
})
