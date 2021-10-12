package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSingleElement
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NærmesteLederService
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class BrukerModelTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerModelImpl(database)

    describe("Beskjed opprettet i BrukerModel") {
        val uuid = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )
        val ansatte = listOf(NærmesteLederService.NærmesteLederFor(
            ansattFnr = mottaker.ansattFnr,
            virksomhetsnummer = mottaker.virksomhetsnummer,
        ))
        val event = Hendelse.BeskjedOpprettet(
            merkelapp = "foo",
            eksternId = "42",
            mottaker = mottaker,
            hendelseId = uuid,
            notifikasjonId = uuid,
            tekst = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
            virksomhetsnummer = mottaker.virksomhetsnummer,
            kildeAppNavn = "",
            produsentId = "",
        )

        context("happy path") {
            queryModel.oppdaterModellEtterHendelse(event)

            it("opprettes beskjed i databasen") {
                val notifikasjoner =
                    queryModel.hentNotifikasjoner(
                        mottaker.naermesteLederFnr,
                        emptyList(),
                        ansatte,
                    )
                notifikasjoner shouldHaveSingleElement BrukerModel.Beskjed(
                    merkelapp = "foo",
                    eksternId = "42",
                    mottaker = mottaker,
                    tekst = "teste",
                    grupperingsid = "gr1",
                    lenke = "foo.no/bar",
                    opprettetTidspunkt = event.opprettetTidspunkt,
                    id = uuid,
                    klikketPaa = false
                )
            }
        }

        context("notifikasjon mottas flere ganger (fra kafka f.eks.)") {
            queryModel.oppdaterModellEtterHendelse(event)

            shouldNotThrowAny {
                queryModel.oppdaterModellEtterHendelse(event)
            }

            it("beskjeden er fortsatt uendret i databasen") {
                val notifikasjoner =
                    queryModel.hentNotifikasjoner(
                        mottaker.naermesteLederFnr,
                        emptyList(),
                        ansatte
                    )
                notifikasjoner shouldHaveSingleElement BrukerModel.Beskjed(
                    merkelapp = "foo",
                    eksternId = "42",
                    mottaker = mottaker,
                    tekst = "teste",
                    grupperingsid = "gr1",
                    lenke = "foo.no/bar",
                    opprettetTidspunkt = event.opprettetTidspunkt,
                    id = uuid,
                    klikketPaa = false
                )
            }
        }
    }
})
