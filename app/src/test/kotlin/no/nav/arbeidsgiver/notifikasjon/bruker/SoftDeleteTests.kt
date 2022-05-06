package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class SoftDeleteTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)
    val nærmesteLederModel = NærmesteLederModelImpl(database)

    describe("SoftDelete av notifikasjon") {
        val uuid1 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val uuid2 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130004")

        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )

        val opprettEvent = fun (id: UUID) = BeskjedOpprettet(
            merkelapp = "foo",
            eksternId = id.toString(),
            mottakere = listOf(mottaker),
            hendelseId = id,
            notifikasjonId = id,
            tekst = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
            virksomhetsnummer = mottaker.virksomhetsnummer,
            kildeAppNavn = "",
            produsentId = "",
            eksterneVarsler = listOf(),
            hardDelete = null,
        )

        val softDeleteEvent = SoftDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = uuid1,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            deletedAt = OffsetDateTime.MAX,
            kildeAppNavn = "",
            produsentId = "",
        )


        it("oppretter to beskjeder i databasen") {
            queryModel.oppdaterModellEtterHendelse(opprettEvent(uuid1))
            queryModel.oppdaterModellEtterHendelse(opprettEvent(uuid2))
            nærmesteLederModel.oppdaterModell(
                NærmesteLederModel.NarmesteLederLeesah(
                    narmesteLederId = uuid("432"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null,
                )
            )

            val notifikasjoner =
                queryModel.hentNotifikasjoner(
                    mottaker.naermesteLederFnr,
                    Tilganger.EMPTY,
                )
                    .map { it.id }
                    .sorted()

            notifikasjoner shouldContainExactly listOf(uuid1, uuid2).sorted()
        }

        it("sletter kun ønsket beskjed") {
            queryModel.oppdaterModellEtterHendelse(softDeleteEvent)
            val notifikasjonerEtterSletting = queryModel.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                Tilganger.EMPTY,
            )
                .map { it.id }

            notifikasjonerEtterSletting shouldContainExactly listOf(uuid2)
        }
    }
})

