package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NærmesteLederService
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class SoftDeleteTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerModelImpl(database)

    describe("SoftDelete av notifikasjon") {
        val uuid1 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val uuid2 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130004")

        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )

        val ansatte = listOf(NærmesteLederService.NærmesteLederFor(
            ansattFnr = mottaker.ansattFnr,
            virksomhetsnummer = mottaker.virksomhetsnummer,
        ))

        val opprettEvent = fun (id: UUID) = Hendelse.BeskjedOpprettet(
            merkelapp = "foo",
            eksternId = id.toString(),
            mottaker = mottaker,
            hendelseId = id,
            notifikasjonId = id,
            tekst = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
            virksomhetsnummer = mottaker.virksomhetsnummer,
            kildeAppNavn = "",
            produsentId = "",
        )

        val softDeleteEvent = Hendelse.SoftDelete(
            hendelseId = UUID.randomUUID(),
            notifikasjonId = uuid1,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            deletedAt = OffsetDateTime.MAX,
            kildeAppNavn = "",
            produsentId = "",
        )


        it("oppretter to beskjeder i databasen") {
            queryModel.oppdaterModellEtterHendelse(opprettEvent(uuid1))
            queryModel.oppdaterModellEtterHendelse(opprettEvent(uuid2))

            val notifikasjoner =
                queryModel.hentNotifikasjoner(
                    mottaker.naermesteLederFnr,
                    emptyList(),
                    ansatte,
                )
                    .map { it.id }
                    .sorted()

            notifikasjoner shouldContainExactly listOf(uuid1, uuid2).sorted()
        }

        it("sletter kun ønsket beskjed") {
            queryModel.oppdaterModellEtterHendelse(softDeleteEvent)
            val notifikasjonerEtterSletting = queryModel.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                emptyList(),
                ansatte,
            )
                .map { it.id }

            notifikasjonerEtterSletting shouldContainExactly listOf(uuid2)
        }
    }
})

