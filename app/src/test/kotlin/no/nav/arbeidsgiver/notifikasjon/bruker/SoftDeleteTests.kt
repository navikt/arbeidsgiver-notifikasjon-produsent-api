package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class SoftDeleteTests : DescribeSpec({
    val virksomhetsnummer = "1"

    val mottaker = NærmesteLederMottaker(
        naermesteLederFnr = "314",
        ansattFnr = "33314",
        virksomhetsnummer = virksomhetsnummer
    )


    describe("SoftDelete av notifikasjon") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)


        val beskjed1 = brukerRepository.beskjedOpprettet(
            mottakere = listOf(mottaker),
            virksomhetsnummer = mottaker.virksomhetsnummer,
        )
        val beskjed2 = brukerRepository.beskjedOpprettet(
            mottakere = listOf(mottaker),
            virksomhetsnummer = mottaker.virksomhetsnummer,
        )

        it("oppretter to beskjeder i databasen") {
            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = uuid("432"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null,
                )
            )

            val notifikasjoner =
                brukerRepository.hentNotifikasjoner(
                    mottaker.naermesteLederFnr,
                    AltinnTilganger(
                        harFeil = false,
                        tilganger = listOf()
                    ),
                )
                    .map { it.id }
                    .sorted()

            notifikasjoner shouldContainExactly listOf(beskjed1.notifikasjonId, beskjed2.notifikasjonId).sorted()
        }

        it("sletter kun ønsket beskjed") {
            brukerRepository.oppdaterModellEtterHendelse(
                SoftDelete(
                    hendelseId = UUID.randomUUID(),
                    aggregateId = beskjed1.notifikasjonId,
                    virksomhetsnummer = mottaker.virksomhetsnummer,
                    deletedAt = OffsetDateTime.MAX,
                    kildeAppNavn = "",
                    produsentId = "",
                    grupperingsid = null,
                    merkelapp = null,
                )
            )
            val notifikasjonerEtterSletting = brukerRepository.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                ),
            )
                .map { it.id }

            notifikasjonerEtterSletting shouldContainExactly listOf(beskjed2.notifikasjonId)
        }
    }

    describe("SoftDelete cascader på sak") {
        it("sletter alle notifikasjoner knyttet til en sak") {
            val database = testDatabase(Bruker.databaseConfig)
            val brukerRepository = BrukerRepositoryImpl(database)

            brukerRepository.oppdaterModellEtterNærmesteLederLeesah(
                NarmesteLederLeesah(
                    narmesteLederId = uuid("432"),
                    fnr = mottaker.ansattFnr,
                    narmesteLederFnr = mottaker.naermesteLederFnr,
                    orgnummer = mottaker.virksomhetsnummer,
                    aktivTom = null,
                )
            )

            val sak1 = brukerRepository.sakOpprettet(
                grupperingsid = "1",
                mottakere = listOf(mottaker),
            )
            val sak2 = brukerRepository.sakOpprettet(
                grupperingsid = "2",
                mottakere = listOf(mottaker),
            )
            val oppgave1 = brukerRepository.oppgaveOpprettet(
                sak = sak1,
            )
            val oppgave2 = brukerRepository.oppgaveOpprettet(
                sak = sak2,
            )
            val beskjed1 = brukerRepository.beskjedOpprettet(
                sak = sak1,
            )
            val beskjed2 = brukerRepository.beskjedOpprettet(
                sak = sak2,
            )
            val kalenderavtale1 = brukerRepository.kalenderavtaleOpprettet(
                sak = sak1,
            )
            val kalenderavtale2 = brukerRepository.kalenderavtaleOpprettet(
                sak = sak2,
            )

            brukerRepository.softDelete(
                sak = sak1
            )

            val notifikasjoner = brukerRepository.hentNotifikasjoner(
                mottaker.naermesteLederFnr,
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                ),
            ).map { it.id }


            val sak1EtterSletting = brukerRepository.hentSakById(
                id = sak1.sakId,
                fnr = mottaker.naermesteLederFnr,
                altinnTilganger = AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                ),
            )
            val sak2EtterSletting = brukerRepository.hentSakById(
                id = sak2.sakId,
                fnr = mottaker.naermesteLederFnr,
                altinnTilganger = AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf()
                ),
            )

            sak1EtterSletting shouldBe null
            sak2EtterSletting shouldNotBe null

            notifikasjoner shouldNotContain oppgave1.notifikasjonId
            notifikasjoner shouldContain oppgave2.notifikasjonId
            notifikasjoner shouldNotContain beskjed1.notifikasjonId
            notifikasjoner shouldContain beskjed2.notifikasjonId
            notifikasjoner shouldNotContain kalenderavtale1.notifikasjonId
            notifikasjoner shouldContain kalenderavtale2.notifikasjonId

        }
    }
})

