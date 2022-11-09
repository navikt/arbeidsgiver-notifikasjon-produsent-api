package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class HardDeleteTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    describe("HardDelete av notifikasjon") {
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

        val hardDeleteEvent = HardDelete(
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
            queryModel.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = uuid("43"),
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
            queryModel.oppdaterModellEtterHendelse(hardDeleteEvent)
            val notifikasjonerEtterSletting = queryModel.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                Tilganger.EMPTY,
            )
                .map { it.id }

            notifikasjonerEtterSletting shouldContainExactly listOf(uuid2)
        }
    }

    describe("HardDelete av sak") {
        val uuid1 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val uuid2 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130004")

        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )

        val opprettEvent = fun (id: UUID) = HendelseModel.SakOpprettet(
            merkelapp = "foo",
            mottakere = listOf(mottaker),
            hendelseId = id,
            sakId = id,
            tittel = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            virksomhetsnummer = mottaker.virksomhetsnummer,
            kildeAppNavn = "",
            produsentId = "",
            mottattTidspunkt = OffsetDateTime.now(),
            oppgittTidspunkt = null,
            hardDelete = null,
        )
        val opprettStatusEvent = fun (id: UUID) = HendelseModel.NyStatusSak(
            hendelseId = id,
            sakId = id,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            kildeAppNavn = "",
            produsentId = "",
            mottattTidspunkt = OffsetDateTime.now(),
            oppgittTidspunkt = null,
            status = HendelseModel.SakStatus.MOTTATT,
            overstyrStatustekstMed = null,
            idempotensKey = IdempotenceKey.initial(),
            hardDelete = null,
            nyLenkeTilSak = null,
        )

        val hardDeleteEvent = HardDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = uuid1,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            deletedAt = OffsetDateTime.MAX,
            kildeAppNavn = "",
            produsentId = "",
        )


        it("oppretter to saker i databasen") {
            queryModel.oppdaterModellEtterHendelse(opprettEvent(uuid1))
            queryModel.oppdaterModellEtterHendelse(opprettStatusEvent(uuid1))
            queryModel.oppdaterModellEtterHendelse(opprettEvent(uuid2))
            queryModel.oppdaterModellEtterHendelse(opprettStatusEvent(uuid2))
            queryModel.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = uuid("43"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null,
                )
            )

            val notifikasjoner =
                queryModel.hentSaker(
                    fnr = mottaker.naermesteLederFnr,
                    virksomhetsnummer = mottaker.virksomhetsnummer,
                    tilganger = Tilganger.EMPTY,
                    tekstsoek = null,
                    offset = 0,
                    limit = Integer.MAX_VALUE
                ).saker
                    .map { it.sakId }
                    .sorted()

            notifikasjoner shouldContainExactly listOf(uuid1, uuid2).sorted()
        }

        it("sletter kun ønsket sak") {
            queryModel.oppdaterModellEtterHendelse(hardDeleteEvent)
            val sakerEtterSletting = queryModel.hentSaker(
                fnr = mottaker.naermesteLederFnr,
                virksomhetsnummer = mottaker.virksomhetsnummer,
                tilganger = Tilganger.EMPTY,
                tekstsoek = null,
                offset = 0,
                limit = Integer.MAX_VALUE
            ).saker
                .map { it.sakId }

            sakerEtterSletting shouldContainExactly listOf(uuid2)
        }
    }
})

