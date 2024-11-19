package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRessursMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime

class AltinnTilgangsstyringTests : DescribeSpec({
    describe("Tilgangsstyring med altinn-tjenester") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)

        for ((id, mottaker) in mapOf(
            "0" to  AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "HarTilgang0",
                serviceEdition = "serviceedition1",
            ),
            "1" to  AltinnRessursMottaker(
                virksomhetsnummer = "1",
                ressursId = "nav_test_HarTilgang1",
            ),
            "2" to  NærmesteLederMottaker(
                virksomhetsnummer = "1",
                ansattFnr = "IkkeTilgang2",
                naermesteLederFnr = "1",
            ),
            "3" to  AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "IkkeTilgang3",
                serviceEdition = "1",
            ),
        )) {
            val uuid = uuid(id)
            brukerRepository.beskjedOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid,
                merkelapp = "m",
                eksternId = uuid.toString(),
                mottakere = listOf(mottaker),
                opprettetTidspunkt = OffsetDateTime.parse("2020-02-02T02:02:02+02"),
            )
        }

        val notifikasjoner = brukerRepository.hentNotifikasjoner(
            fnr = "",
            altinnTilganger = AltinnTilganger(
                harFeil = false,
                tilganger = listOf(
                    AltinnTilgang(
                        orgNr = "1",
                        tilgang = "HarTilgang0:serviceedition1"
                    ),
                    AltinnTilgang(
                        orgNr = "1",
                        tilgang = "nav_test_HarTilgang1"
                    )
                ),
            )
        )

        it("har fått riktig antall meldinger") {
            notifikasjoner shouldHaveSize 2
        }

        it("har fått riktig id-er") {
            notifikasjoner.map { it.id } shouldContainExactlyInAnyOrder listOf(uuid("0"), uuid("1"))
        }
    }
})