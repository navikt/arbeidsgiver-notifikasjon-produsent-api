package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class SoftDeleteTests : DescribeSpec({

    describe("SoftDelete av notifikasjon") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)

        val mottaker = NærmesteLederMottaker(
            naermesteLederFnr = "314",
            ansattFnr = "33314",
            virksomhetsnummer = "1337"
        )


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
                    Tilganger.EMPTY,
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
                Tilganger.EMPTY,
            )
                .map { it.id }

            notifikasjonerEtterSletting shouldContainExactly listOf(beskjed2.notifikasjonId)
        }
    }
})

