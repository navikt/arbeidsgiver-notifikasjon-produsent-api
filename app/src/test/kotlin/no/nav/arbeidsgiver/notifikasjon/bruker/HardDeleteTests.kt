package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class HardDeleteTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    describe("HardDelete av notifikasjon") {
        val uuid1 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130003")
        val uuid2 = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac130004")

        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )

        suspend fun opprettBeskjed(id: UUID) = brukerRepository.beskjedOpprettet(
            mottakere = listOf(mottaker),
            notifikasjonId = id,
            virksomhetsnummer = mottaker.virksomhetsnummer,
        )

        val hardDeleteEvent = HardDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = uuid1,
            virksomhetsnummer = mottaker.virksomhetsnummer,
            deletedAt = OffsetDateTime.MAX,
            kildeAppNavn = "",
            produsentId = "",
            grupperingsid = null,
            merkelapp = null,
        )


        opprettBeskjed(uuid1)
        opprettBeskjed(uuid2)
        brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
            NarmesteLederLeesah(
                narmesteLederId = uuid("43"),
                fnr = mottaker.ansattFnr,
                narmesteLederFnr = mottaker.naermesteLederFnr,
                orgnummer = mottaker.virksomhetsnummer,
                aktivTom = null,
            )
        )

        val notifikasjoner =
            brukerRepository.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                Tilganger.EMPTY,
            )
                .map { it.id }
                .sorted()

        it("oppretter to beskjeder i databasen") {
            notifikasjoner shouldContainExactly listOf(uuid1, uuid2).sorted()
        }

        it("sletter kun ønsket beskjed") {
            brukerRepository.oppdaterModellEtterHendelse(hardDeleteEvent)
            val notifikasjonerEtterSletting = brukerRepository.hentNotifikasjoner(
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

        suspend fun opprettEvent(id: UUID) = brukerRepository.sakOpprettet(
            merkelapp = "foo",
            mottakere = listOf(mottaker),
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
        suspend fun opprettStatusEvent(sak: HendelseModel.SakOpprettet) = brukerRepository.nyStatusSak(
            sak,
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
            grupperingsid = null,
            merkelapp = null,
        )


        it("oppretter to saker i databasen") {
            val sak1 = opprettEvent(uuid1)
            brukerRepository.oppdaterModellEtterHendelse(opprettStatusEvent(sak1))
            val sak2 = opprettEvent(uuid2)
            brukerRepository.oppdaterModellEtterHendelse(opprettStatusEvent(sak2))
            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = uuid("43"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null,
                )
            )

            val notifikasjoner =
                brukerRepository.hentSaker(
                    fnr = mottaker.naermesteLederFnr,
                    virksomhetsnummer = listOf(mottaker.virksomhetsnummer),
                    tilganger = Tilganger.EMPTY,
                    tekstsoek = null,
                    sakstyper = null,
                    offset = 0,
                    limit = Integer.MAX_VALUE,
                    sortering = BrukerAPI.SakSortering.OPPDATERT,
                    oppgaveTilstand = null,
                ).saker
                    .map { it.sakId }
                    .sorted()

            notifikasjoner shouldContainExactly listOf(uuid1, uuid2).sorted()
        }

        it("sletter kun ønsket sak") {
            brukerRepository.oppdaterModellEtterHendelse(hardDeleteEvent)
            val sakerEtterSletting = brukerRepository.hentSaker(
                fnr = mottaker.naermesteLederFnr,
                virksomhetsnummer = listOf(mottaker.virksomhetsnummer),
                tilganger = Tilganger.EMPTY,
                tekstsoek = null,
                sakstyper = null,
                offset = 0,
                limit = Integer.MAX_VALUE,
                sortering = BrukerAPI.SakSortering.OPPDATERT,
                oppgaveTilstand = null,
            ).saker
                .map { it.sakId }

            sakerEtterSletting shouldContainExactly listOf(uuid2)
        }
    }
})

