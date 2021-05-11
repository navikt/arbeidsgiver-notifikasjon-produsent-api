package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSingleElement
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class QueryModelTests : DescribeSpec({
    val dataSource = runBlocking { Database.createDataSource() }
    listener(PostgresTestListener(dataSource))

    describe("QueryModel") {
        describe("#queryModelBuilderProcessor()") {
            context("når event er BeskjedOpprettet") {
                val mottaker = FodselsnummerMottaker(
                    fodselsnummer = "314",
                    virksomhetsnummer = "1337"
                )
                val event = Hendelse.BeskjedOpprettet(
                    merkelapp = "foo",
                    eksternId = "42",
                    mottaker = mottaker,
                    guid = UUID.randomUUID(),
                    tekst = "teste",
                    grupperingsid = "gr1",
                    lenke = "foo.no/bar",
                    opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
                    virksomhetsnummer = mottaker.virksomhetsnummer
                )

                beforeEach {
                    QueryModel.builderProcessor(dataSource, event)
                }

                it("opprettes beskjed i databasen") {
                    val notifikasjoner =
                        QueryModel.hentNotifikasjoner(
                            dataSource,
                            mottaker.fodselsnummer,
                            emptyList()
                        )
                    notifikasjoner shouldHaveSingleElement QueryModel.QueryBeskjedMedId(
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
