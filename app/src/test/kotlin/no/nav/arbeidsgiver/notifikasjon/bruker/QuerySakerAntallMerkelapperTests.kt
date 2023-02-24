package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Sakstype
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus.MOTTATT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*

private val tilgang1 = AltinnMottaker(virksomhetsnummer = "43", serviceCode = "5441", serviceEdition = "1")
private val tilgang2 = AltinnMottaker(virksomhetsnummer = "44", serviceCode = "5441", serviceEdition = "1")

class QuerySakerAntallMerkelapperTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        altinn = AltinnStub(
            "0".repeat(11) to BrukerModel.Tilganger(
                tjenestetilganger = listOf(tilgang1, tilgang2).map {
                    BrukerModel.Tilgang.Altinn(
                        virksomhet = it.virksomhetsnummer,
                        servicecode = it.serviceCode,
                        serviceedition = it.serviceEdition
                    )
                },
            )
        ),
        brukerRepository = brukerRepository,
    )

    describe("antall i sakstype (Query.saker)") {
        for (merkelapp in listOf("merkelapp1", "merkelapp2")) {
            for (tittel in listOf("sykmelding", "refusjon")) {
                brukerRepository.opprettSak(
                    tilgang = tilgang1,
                    merkelapp = merkelapp,
                    tittel = tittel,
                )
            }
        }
        for (tittel in listOf("sykmelding", "refusjon")) {
            brukerRepository.opprettSak(
                tilgang = tilgang2,
                merkelapp = "merkelapp1",
                tittel = tittel,
            )
        }

        it("ingen virksomheter gir ingen sakstyper") {
            val sakstyper = engine.hentSakstyper(virksomhetsnumre = listOf())
            sakstyper shouldContainExactlyInAnyOrder listOf()
        }
        it("ingen virksomheter gir ingen sakstyper, selv med merkelapp valgt") {
            val sakstyper = engine.hentSakstyper(
                virksomhetsnumre = listOf(),
                sakstyper = listOf("merkelapp1")
            )
            sakstyper shouldContainExactlyInAnyOrder listOf()
        }

        it("filter på virksomhet og merkelapp") {
            val sakstyper = engine.hentSakstyper(
                virksomhetsnumre = listOf(tilgang1.virksomhetsnummer),
                sakstyper = listOf("merkelapp1"),
            )
            sakstyper shouldContainExactlyInAnyOrder listOf(
                Sakstype("merkelapp1", 2),
                Sakstype("merkelapp2", 2),
            )
        }

        it("filter på virksomhet1 og tekstsøk") {
            val sakstyper = engine.hentSakstyper(
                virksomhetsnumre = listOf(tilgang1.virksomhetsnummer),
                tekstsoek = "sykmelding"
            )
            sakstyper shouldContainExactlyInAnyOrder listOf(
                Sakstype("merkelapp1", 1),
                Sakstype("merkelapp2", 1)
            )
        }
        it("filter på virksomhet2 og tekstsøk") {
            val sakstyper = engine.hentSakstyper(
                virksomhetsnumre = listOf(tilgang2.virksomhetsnummer),
                tekstsoek = "sykmelding"
            )
            sakstyper shouldContainExactlyInAnyOrder listOf(
                Sakstype("merkelapp1", 1),
            )
        }
        it("filter på virksomhet2 og tekstsøk") {
            val sakstyper = engine.hentSakstyper(
                virksomhetsnumre = listOf(tilgang2.virksomhetsnummer),
                tekstsoek = "sykmelding",
                sakstyper = listOf("merkelapp2")
            )
            sakstyper shouldContainExactlyInAnyOrder listOf(
                Sakstype("merkelapp1", 1),
            )
        }

        it("ingen filter gir alt") {
            val sakstyper = engine.hentSakstyper()
            sakstyper shouldContainExactlyInAnyOrder listOf(
                Sakstype("merkelapp1", 4),
                Sakstype("merkelapp2", 2)
            )
        }
    }
})

private suspend fun BrukerRepository.opprettSak(
    tilgang: AltinnMottaker,
    merkelapp: String,
    tittel: String,
): SakOpprettet {
    val sakId = UUID.randomUUID()
    val oppgittTidspunkt = OffsetDateTime.parse("2022-01-01T13:37:30+02:00")
    val sak = SakOpprettet(
        hendelseId = sakId,
        sakId = sakId,
        grupperingsid = sakId.toString(),
        virksomhetsnummer = tilgang.virksomhetsnummer,
        produsentId = "test",
        kildeAppNavn = "test",
        merkelapp = merkelapp,
        mottakere = listOf(tilgang),
        tittel = tittel,
        lenke = "#foo",
        oppgittTidspunkt = oppgittTidspunkt,
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    ).also {
        oppdaterModellEtterHendelse(it)
    }
    NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = sak.virksomhetsnummer,
        produsentId = sak.produsentId,
        kildeAppNavn = sak.kildeAppNavn,
        sakId = sak.sakId,
        status = MOTTATT,
        overstyrStatustekstMed = "noe",
        mottattTidspunkt = oppgittTidspunkt,
        idempotensKey = IdempotenceKey.initial(),
        oppgittTidspunkt = null,
        hardDelete = null,
        nyLenkeTilSak = null,
    ).also { hendelse ->
        oppdaterModellEtterHendelse(hendelse)
    }
    return sak
}

private fun TestApplicationEngine.hentSakstyper(
    virksomhetsnumre: List<String> = listOf(tilgang1.virksomhetsnummer, tilgang2.virksomhetsnummer),
    sakstyper: List<String>? = null,
    tekstsoek: String? = null,
    offset: Int? = null,
    limit: Int? = null,
    sortering: BrukerAPI.SakSortering = BrukerAPI.SakSortering.OPPDATERT,
): List<Sakstype> = brukerApi(
    GraphQLRequest(
        """
            query hentSaker(${'$'}virksomhetsnumre: [String!]!, ${'$'}sakstyper: [String!], ${'$'}tekstsoek: String, ${'$'}sortering: SakSortering!, ${'$'}offset: Int, ${'$'}limit: Int){
                saker(virksomhetsnumre: ${'$'}virksomhetsnumre, sakstyper: ${'$'}sakstyper, tekstsoek: ${'$'}tekstsoek, sortering: ${'$'}sortering, offset: ${'$'}offset, limit: ${'$'}limit) {
                    sakstyper {
                        navn
                        antall
                    }
                }
            }
    """.trimIndent(),
        "hentSaker",
        mapOf(
            "virksomhetsnumre" to virksomhetsnumre,
            "sakstyper" to sakstyper,
            "tekstsoek" to tekstsoek,
            "sortering" to sortering,
            "offset" to offset,
            "limit" to limit,
        )
    )
)
    .getTypedContent("$.saker.sakstyper")
