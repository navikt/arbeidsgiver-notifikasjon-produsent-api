package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSingleElement
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.createDataSource
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class QueryModelTests : DescribeSpec({
    val dataSource = runBlocking { createDataSource() }
    listener(PostgresTestListener(dataSource))

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
                    opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS)
                )

                beforeEach {
                    queryModelBuilderProcessor(dataSource, event)
                }

                it("opprettes beskjed i databasen") {
                    val notifikasjoner =
                        QueryModelRepository.hentNotifikasjoner(
                            dataSource,
                            mottaker.fodselsnummer,
                            emptyList()
                        )
                    notifikasjoner shouldHaveSingleElement QueryBeskjedMedId(
                        merkelapp = "foo",
                        eksternId = "42",
                        mottaker = mottaker,
                        tekst = "teste",
                        grupperingsid = "gr1",
                        lenke = "foo.no/bar",
                        opprettetTidspunkt = event.opprettetTidspunkt,
                        id = "1"
                    )
                }
            }
        }
    }
})
