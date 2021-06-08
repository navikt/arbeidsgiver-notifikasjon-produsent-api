package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

class BrukerKlikkGraphQL_QueryModell_IntegrasjonTests: DescribeSpec({
    val database = testDatabase()
    val queryModel = QueryModelImpl(database)

    val engine = ktorTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = AltinnStub(),
            brreg = BrregStub(),
            queryModelFuture = CompletableFuture.completedFuture(queryModel),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk()
        )
    )

    describe("Brukerklikk-oppførsel") {
        val uuid = UUID.fromString("c39986f2-b31a-11eb-8529-0242ac130003")
        val fnr = "00000000000"
        val virksomhetsnummer = "1234"

        val beskjedOpprettet = Hendelse.BeskjedOpprettet(
            virksomhetsnummer = virksomhetsnummer,
            mottaker = NærmesteLederMottaker(fnr, "", virksomhetsnummer),
            opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
            id = uuid,
            merkelapp = "",
            eksternId = "",
            tekst = "",
            lenke = "",
        )
        queryModel.oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet)

        /* sjekk at beskjed ikke er klikket på */
        val response = engine.brukerApi("""
            {
                notifikasjoner {
                    ...on Beskjed {
                        brukerKlikk {
                            klikketPaa
                        }
                    }
                }
            }
        """)

        val klikkMarkørFørKlikk = response.getTypedContent<Boolean>("/notifikasjoner/0/brukerKlikk/klikketPaa")

        it("notifikasjon er ikke klikket på") {
            klikkMarkørFørKlikk shouldBe false
        }


        val brukerKlikket = Hendelse.BrukerKlikket(
            virksomhetsnummer = virksomhetsnummer,
            fnr = fnr,
            notifikasjonsId = uuid
        )

        queryModel.oppdaterModellEtterBrukerKlikket(brukerKlikket)

        /* sjekk at beskjed ikke er klikket på */
        val responseEtterKlikk = engine.brukerApi("""
            {
                notifikasjoner {
                    ...on Beskjed {
                        brukerKlikk {
                            klikketPaa
                        }
                    }
                }
            }
        """)
        val klikkMarkørEtterKlikk = responseEtterKlikk.getTypedContent<Boolean>("/notifikasjoner/0/brukerKlikk/klikketPaa")

        it("notifikasjon er klikket på") {
            klikkMarkørEtterKlikk shouldBe true
        }
    }
})
