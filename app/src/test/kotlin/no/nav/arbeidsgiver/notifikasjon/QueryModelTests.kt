package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSingleElement
import no.nav.arbeidsgiver.notifikasjon.hendelse.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.FodselsnummerMottaker
import java.time.Instant
import java.util.*

class QueryModelTests : DescribeSpec({
    listener(PostgresTestListener())

    describe("QueryModel") {
        describe("#queryModelBuilderProcessor()") {
            context("når event er BeskjedOpprettet") {
                val mottaker = FodselsnummerMottaker(
                    fodselsnummer = "314",
                    virksomhetsnummer = "1337"
                )
                val event = BeskjedOpprettet(
                    merkelapp = "foo",
                    eksternId = "42",
                    mottaker = mottaker,
                    guid = UUID.randomUUID(),
                    tekst = "teste",
                    grupperingsid = "gr1",
                    lenke = "foo.no/bar",
                    opprettetTidspunkt = Instant.now()
                )

                beforeEach {
                    queryModelBuilderProcessor(event)
                }

                it("opprettes beskjed i databasen") {
                    val notifikasjoner =
                        QueryModelRepository.hentNotifikasjoner(mottaker.fodselsnummer, emptyList())
                    notifikasjoner shouldHaveSingleElement QueryBeskjed(
                        merkelapp = "foo",
                        eksternId = "42",
                        mottaker = mottaker,
                        tekst = "teste",
                        grupperingsid = "gr1",
                        lenke = "foo.no/bar",
                        opprettetTidspunkt = event.opprettetTidspunkt
                    )
                }
            }
        }
    }
})
