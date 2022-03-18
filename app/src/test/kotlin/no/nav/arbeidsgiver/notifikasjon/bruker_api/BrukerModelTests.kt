package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSingleElement
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.*
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class BrukerModelTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)
    val nærmesteLederModel = NærmesteLederModelImpl(database)

    describe("Beskjed opprettet i BrukerModel") {
        val uuid = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )
        val event = BeskjedOpprettet(
            merkelapp = "foo",
            eksternId = "42",
            mottakere = listOf(mottaker),
            hendelseId = uuid,
            notifikasjonId = uuid,
            tekst = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
            virksomhetsnummer = mottaker.virksomhetsnummer,
            kildeAppNavn = "",
            produsentId = "",
            eksterneVarsler = listOf(),
        )


        context("happy path") {
            nærmesteLederModel.oppdaterModell(
                NærmesteLederModel.NarmesteLederLeesah(
                    narmesteLederId = uuid("4321"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null,
                )
            )
            queryModel.oppdaterModellEtterHendelse(event)

            it("opprettes beskjed i databasen") {
                val notifikasjoner =
                    queryModel.hentNotifikasjoner(
                        mottaker.naermesteLederFnr,
                        Tilganger.EMPTY,
                    )
                notifikasjoner shouldHaveSingleElement BrukerModel.Beskjed(
                    merkelapp = "foo",
                    eksternId = "42",
                    virksomhetsnummer = mottaker.virksomhetsnummer,
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
            nærmesteLederModel.oppdaterModell(
                NærmesteLederModel.NarmesteLederLeesah(
                    narmesteLederId = uuid("4321"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null,
                )
            )
            queryModel.oppdaterModellEtterHendelse(event)

            shouldNotThrowAny {
                queryModel.oppdaterModellEtterHendelse(event)
            }

            it("beskjeden er fortsatt uendret i databasen") {
                val notifikasjoner =
                    queryModel.hentNotifikasjoner(
                        mottaker.naermesteLederFnr,
                        Tilganger.EMPTY,
                    )
                notifikasjoner shouldHaveSingleElement BrukerModel.Beskjed(
                    merkelapp = "foo",
                    eksternId = "42",
                    virksomhetsnummer = mottaker.virksomhetsnummer,
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
