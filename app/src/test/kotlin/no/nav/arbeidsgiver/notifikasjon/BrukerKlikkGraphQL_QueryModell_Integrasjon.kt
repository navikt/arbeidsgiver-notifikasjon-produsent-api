package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

class BrukerKlikkGraphQL_QueryModell_Integrasjon: DescribeSpec({

    val altinn = object : Altinn {
        override fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<QueryModel.Tilgang>()
    }

    val database = runBlocking { Database.openDatabase() }
    val queryModel = QueryModel(database)
    listener(PostgresTestListener(database))

    val engine = ktorTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
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
            mottaker = FodselsnummerMottaker(fnr, virksomhetsnummer),
            opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
            uuid = uuid,
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
                        klikketPaa
                    }
                }
            }
        """)

        val klikkMarkørFørKlikk = response.getTypedContent<Boolean>("/notifikasjoner/0/klikketPaa")

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
                        klikketPaa
                    }
                }
            }
        """)
        val klikkMarkørEtterKlikk = responseEtterKlikk.getTypedContent<Boolean>("/notifikasjoner/0/klikketPaa")

        it("notifikasjon er klikket på") {
            klikkMarkørEtterKlikk shouldBe true
        }
    }
})
