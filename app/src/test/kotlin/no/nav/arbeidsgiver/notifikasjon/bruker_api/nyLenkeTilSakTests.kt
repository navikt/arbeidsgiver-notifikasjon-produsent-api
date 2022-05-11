package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime

class NyLenkeTilSakTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(
                listOf(
                    BrukerModel.Tilgang.Altinn(
                        virksomhet = "1",
                        servicecode = "1",
                        serviceedition = "1",
                    )
                )
            )
        }
    )

    fun hentLenke() = engine.brukerApi(
        """ 
                {
                    saker(virksomhetsnummer: "1", offset: 0, limit: 1) {
                        saker {
                            lenke
                        }
                    }
                }"""
    ).getTypedContent<String>("saker/saker/0/lenke")


    describe("endring av lenke i sak") {
        val SakOpprettet = HendelseModel.SakOpprettet(
            hendelseId = uuid("0"),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = uuid("0"),
            grupperingsid = "1",
            merkelapp = "tag",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            tittel = "foo",
            lenke = "#foo",
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.now(),
            hardDelete = null,
        )
        val NyStatusSak = HendelseModel.NyStatusSak(
            hendelseId = uuid("1"),
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = uuid("0"),
            status = HendelseModel.SakStatus.MOTTATT,
            overstyrStatustekstMed = null,
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.now(),
            idempotensKey = IdempotenceKey.initial(),
            hardDelete = null,
            nyLenkeTilSak = null,
        )
        queryModel.oppdaterModellEtterHendelse(SakOpprettet)
        queryModel.oppdaterModellEtterHendelse(NyStatusSak)

        it("Får opprinnelig lenke") {
            hentLenke() shouldBe "#foo"
        }

        queryModel.oppdaterModellEtterHendelse(
            NyStatusSak.copy(
                hendelseId = uuid("2"),
                idempotensKey = IdempotenceKey.userSupplied("20202021"),
                nyLenkeTilSak = "#bar",
            )
        )

        it("Får ny lenke ") {
            hentLenke() shouldBe "#bar"
        }

        queryModel.oppdaterModellEtterHendelse(NyStatusSak.copy(
            hendelseId = uuid("3"),
            idempotensKey = IdempotenceKey.userSupplied("123"),
            nyLenkeTilSak = null,
        ))

        it("status-oppdatering uten endret lenke") {
            hentLenke() shouldBe "#bar"
        }
    }
})