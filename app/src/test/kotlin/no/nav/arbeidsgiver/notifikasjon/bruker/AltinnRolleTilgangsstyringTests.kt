package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRolleMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class AltinnRolleTilgangsstyringTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val model = BrukerRepositoryImpl(database)

    fun lagMelding(id: UUID, rolle: String) = BeskjedOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = id,
        hendelseId = id,
        produsentId = "p",
        kildeAppNavn = "k",
        merkelapp = "m",
        eksternId = id.toString(),
        mottakere = listOf(AltinnRolleMottaker(
            virksomhetsnummer = "1",
            roleDefinitionId = rolle,
            roleDefinitionCode = rolle,
        )),
        tekst = "",
        lenke = "https://dev.nav.no",
        opprettetTidspunkt = OffsetDateTime.parse("2020-02-02T02:02:02+02"),
        eksterneVarsler  = listOf(),
        grupperingsid = null,
        hardDelete = null,
    )

    describe("Tilgangsstyring med altinn rolle") {
        listOf(
            lagMelding(uuid("0"), "HarTilgang0"),
            lagMelding(uuid("1"), "HarTilgang1"),
            lagMelding(uuid("2"), "IkkeTilgang2"),
            lagMelding(uuid("3"), "IkkeTilgang3"),

            ).forEach {
            model.oppdaterModellEtterHendelse(it)
        }

        val notifikasjoner = model.hentNotifikasjoner(
            fnr = "",
            tilganger = Tilganger(
                rolle = listOf("HarTilgang0", "HarTilgang1").map {
                    BrukerModel.Tilgang.AltinnRolle(
                        virksomhet = "1",
                        roleDefinitionId = it,
                        roleDefinitionCode = it,
                    )
                },
                reportee = listOf(),
                tjenestetilganger = listOf(),
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