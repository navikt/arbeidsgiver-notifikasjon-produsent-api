package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class BrukerRepositorySearchTest : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val repo = BrukerRepositoryImpl(database)

    describe("BrukerRepositoryImpl#hentSaker") {
        repo.insertSak("1", "Refusjon graviditet - Gerd - 112233")
        repo.insertSak("2", "Refusjon kronisk syk - Gerd - 112233")
        repo.insertSak("3", "Refusjon kronisk syk - Gerd - 223344")
        repo.insertSak("4", "Refusjon kronisk syk - Per - 123456")
        repo.insertSak("5", "Sykemelding - Per - 123456")
        repo.insertSak("6", "Sykemelding - Gerd - 123456")
        repo.insertSak("7", "Sykemelding - Pål - 111222")
        repo.insertSak("8", "Litt rare symboler // \\x % _")

        withData(
            "" to listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            " " to listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            "zzzzz" to listOf(),
            "gravid" to listOf("1"),
            "    gravid    " to listOf("1"),
            " \tgravid    " to listOf("1"),
            "GRAVID" to listOf("1"),
            "graviditet" to listOf("1"),
            "Graviditet" to listOf("1"),
            "GRAVIDITET" to listOf("1"),
            "11" to listOf("1", "2", "7"),
            "22" to listOf("1", "2", "3", "7"),
            "33" to listOf("1", "2", "3"),
            "123456" to listOf("4", "5", "6"),
            "112233" to listOf("1", "2"),
            "gravid gerd" to listOf("1"),
            "gravid,gerd" to listOf("1"),
            "gravid, gerd" to listOf("1"),
            "   gravid    gerd   " to listOf("1"),
            "gerd gravid" to listOf("1"),
            "gerd\tgravid" to listOf("1"),
            "gerd\t \ngravid\n \r" to listOf("1"),
            "graviditet gerd" to listOf("1"),
            "gerd graviditet" to listOf("1"),
            "syk per" to listOf("4", "5"),
            "gravid per" to listOf(),
            "graviditet per" to listOf(),
            "per gravid" to listOf(),
            "112233 gerd" to listOf("1", "2"),
            "112233 kronisk" to listOf("2"),
            "kronisk 112233" to listOf("2"),
            "1223 kronisk" to listOf("2"),
            "kron 1223" to listOf("2"),
            "112233 gerd gravid" to listOf("1"),
            "gerd 112233 gravid" to listOf("1"),
            "gravid gerd 112233" to listOf("1"),
            "112233 gerd syk" to listOf("2"),
            "syk 112233 gerd" to listOf("2"),
            "syk gerd 112233" to listOf("2"),
            "syk gerd 11%33" to listOf(), // '%' from user is not interpreted as SQL wildcard
            "syk gerd 11_233" to listOf(), // '_' from user is not interpreted as SQL wildcard
            "syk gerd \\11233" to listOf(), // '\\' from user is not interpreted as SQL escape
            "PÅL" to listOf("7"),
            "pål" to listOf("7"),
            "pal" to listOf(),
            "\\" to listOf("8"),
            "\\x" to listOf("8"),
            "_" to listOf("8"),
            "%" to listOf("8"),
            // Other test cases?
            // - turkish i where upper-lower round-trip is not identity function
        ) { (query, result) ->
            repo.search(query) shouldContainExactlyInAnyOrder result.map { uuid(it) }
        }
    }
})


private suspend fun BrukerRepositoryImpl.search(query: String): List<UUID> =
    hentSaker(
        fnr = "1",
        virksomhetsnummer = listOf("1"),
        tilganger = BrukerModel.Tilganger(
            tjenestetilganger = listOf(
                BrukerModel.Tilgang.Altinn(
                    virksomhet = "1",
                    servicecode = "1",
                    serviceedition = "1",
                )
            )
        ),
        tekstsoek = query,
        offset = 0,
        limit = 1000_000,
        sortering = BrukerAPI.SakSortering.OPPDATERT
    )
        .saker
        .map { it.sakId }

private suspend fun BrukerRepositoryImpl.insertSak(id: String, tekst: String) {
    this.oppdaterModellEtterHendelse(
        HendelseModel.SakOpprettet(
            hendelseId = uuid(id),
            virksomhetsnummer = "1",
            produsentId = "",
            kildeAppNavn = "",
            sakId = uuid(id),
            grupperingsid = uuid(id).toString(),
            merkelapp = "",
            mottakere = listOf(HendelseModel.AltinnMottaker("1", "1", "1")),
            tittel = tekst,
            lenke = "",
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.now(),
            hardDelete = null
        )
    )
    this.oppdaterModellEtterHendelse(HendelseModel.NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = "1",
        produsentId = "",
        kildeAppNavn = "",
        sakId = uuid(id),
        status = HendelseModel.SakStatus.MOTTATT,
        overstyrStatustekstMed = null,
        oppgittTidspunkt = null,
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = "x",
        hardDelete = null,
        nyLenkeTilSak = null,
    ))
}
