package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime

class AltinnTilgangsstyringTests : DescribeSpec({
    describe("Tilgangsstyring med altinn-tjenester") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)

        for ((id, serviceCode) in listOf(
            "0" to  "HarTilgang0",
            "1" to  "HarTilgang1",
            "2" to  "IkkeTilgang2",
            "3" to  "IkkeTilgang3",
        )) {
            val uuid = uuid(id)
            brukerRepository.beskjedOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid,
                merkelapp = "m",
                eksternId = uuid.toString(),
                mottakere = listOf(
                    AltinnMottaker(
                        virksomhetsnummer = "1",
                        serviceCode = serviceCode,
                        serviceEdition = "1",
                    )
                ),
                opprettetTidspunkt = OffsetDateTime.parse("2020-02-02T02:02:02+02"),
            )
        }

        val notifikasjoner = brukerRepository.hentNotifikasjoner(
            fnr = "",
            altinnTilganger = AltinnTilganger(
                harFeil = false,
                tilganger = listOf("HarTilgang0", "HarTilgang1").map {
                    AltinnTilgang(
                        orgNr = "1",
                        tilgang = "$it:1"
                    )
                },
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