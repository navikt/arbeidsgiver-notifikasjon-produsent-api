package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.instanceOf
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class KalenderavtaleTests : DescribeSpec({

    val grupperingsid = "g42"
    val eksternId = "heuheu"
    val merkelapp = "tag"

    describe("Kalenderavtale mutations") {
        val (produsentRepository, kafkaProducer, engine) = setupEngine()
        val sakOpprettet = HendelseModel.SakOpprettet(
            virksomhetsnummer = "1",
            merkelapp = "tag",
            grupperingsid = grupperingsid,
            mottakere = listOf(
                HendelseModel.NærmesteLederMottaker(
                    naermesteLederFnr = "12345678910",
                    ansattFnr = "321",
                    virksomhetsnummer = "42"
                )
            ),
            hendelseId = uuid("11"),
            sakId = uuid("11"),
            tittel = "test",
            lenke = "https://nav.no",
            oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
            mottattTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
            kildeAppNavn = "",
            produsentId = "",
            hardDelete = null,
        ).also {
            produsentRepository.oppdaterModellEtterHendelse(it)
        }

        lateinit var nyKalenderavtale: MutationKalenderavtale.NyKalenderavtaleVellykket

        // opprett happy path
        engine.nyKalenderavtale(grupperingsid, merkelapp, eksternId).let { response ->
            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            it("respons inneholder forventet data") {
                nyKalenderavtale =
                    response.getTypedContent<MutationKalenderavtale.NyKalenderavtaleVellykket>("nyKalenderavtale")
                nyKalenderavtale should beOfType<MutationKalenderavtale.NyKalenderavtaleVellykket>()
            }

            it("sends message to kafka") {
                kafkaProducer.hendelser.removeLast().also { hendelse ->
                    hendelse shouldBe instanceOf<HendelseModel.KalenderavtaleOpprettet>()
                    hendelse as HendelseModel.KalenderavtaleOpprettet
                    hendelse.notifikasjonId shouldBe nyKalenderavtale.id
                    hendelse.sakId shouldBe sakOpprettet.sakId
                    hendelse.tilstand shouldBe HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
                    hendelse.lenke shouldBe "https://foo.bar"
                    hendelse.tekst shouldBe "hello world"
                    hendelse.merkelapp shouldBe "tag"
                    hendelse.mottakere.single() shouldBe HendelseModel.NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                    hendelse.startTidspunkt shouldBe LocalDateTime.parse("2024-10-12T07:20:50.52")
                    hendelse.sluttTidspunkt shouldBe LocalDateTime.parse("2024-10-12T08:20:50.52")
                    hendelse.lokasjon shouldBe HendelseModel.Lokasjon(
                        postnummer = "1234",
                        poststed = "Kneika",
                        adresse = "rundt svingen og borti høgget"
                    )
                    hendelse.erDigitalt shouldBe true
                    hendelse.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
                }
            }

            it("updates produsent modell") {
                val id = nyKalenderavtale.id
                produsentRepository.hentNotifikasjon(id).let {
                    it shouldNot beNull()
                    it should beOfType<ProdusentModel.Kalenderavtale>()

                    it as ProdusentModel.Kalenderavtale
                    it.merkelapp shouldBe merkelapp
                    it.virksomhetsnummer shouldBe "42"

                    it.tekst shouldBe "hello world"
                    it.grupperingsid shouldBe grupperingsid
                    it.lenke shouldBe "https://foo.bar"
                    it.eksternId shouldBe eksternId
                    it.tilstand shouldBe ProdusentModel.Kalenderavtale.Tilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
                    it.startTidspunkt shouldBe LocalDateTime.parse("2024-10-12T07:20:50.52")
                    it.sluttTidspunkt shouldBe LocalDateTime.parse("2024-10-12T08:20:50.52")
                    it.lokasjon shouldBe ProdusentModel.Kalenderavtale.Lokasjon(
                        postnummer = "1234",
                        poststed = "Kneika",
                        adresse = "rundt svingen og borti høgget"
                    )
                    it.digitalt shouldBe true
                }
            }
        }


        // oppdater happy path
        engine.kalenderavtaleOppdater(nyKalenderavtale.id).let { response ->
            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }
            lateinit var oppdaterVellykket: MutationKalenderavtale.KalenderavtaleOppdaterVellykket
            it("respons inneholder forventet data") {
                oppdaterVellykket =
                    response.getTypedContent<MutationKalenderavtale.KalenderavtaleOppdaterVellykket>("kalenderavtaleOppdater")
                oppdaterVellykket should beOfType<MutationKalenderavtale.KalenderavtaleOppdaterVellykket>()
            }
            it("sends message to kafka") {
                kafkaProducer.hendelser.removeLast().also { hendelse ->
                    hendelse shouldBe instanceOf<HendelseModel.KalenderavtaleOppdatert>()
                    hendelse as HendelseModel.KalenderavtaleOppdatert
                    hendelse.notifikasjonId shouldBe nyKalenderavtale.id
                    hendelse.tilstand shouldBe HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_HAR_GODTATT
                    hendelse.lenke shouldBe "https://foo.bar"
                    hendelse.tekst shouldBe "hello world"
                    hendelse.startTidspunkt shouldBe LocalDateTime.parse("2024-10-12T07:20:50.52")
                    hendelse.sluttTidspunkt shouldBe LocalDateTime.parse("2024-10-12T08:20:50.52")
                    hendelse.lokasjon shouldBe HendelseModel.Lokasjon(
                        postnummer = "1234",
                        poststed = "Kneika",
                        adresse = "rundt svingen og borti høgget"
                    )
                    hendelse.erDigitalt shouldBe true
                    hendelse.hardDelete shouldBe instanceOf(HendelseModel.HardDeleteUpdate::class)
                }
            }
            it("updates produsent modell") {
                produsentRepository.hentNotifikasjon(oppdaterVellykket.id).let {
                    it shouldNot beNull()
                    it should beOfType<ProdusentModel.Kalenderavtale>()

                    it as ProdusentModel.Kalenderavtale
                    it.merkelapp shouldBe merkelapp
                    it.virksomhetsnummer shouldBe "42"

                    it.tekst shouldBe "hello world"
                    it.grupperingsid shouldBe grupperingsid
                    it.lenke shouldBe "https://foo.bar"
                    it.eksternId shouldBe eksternId
                    it.tilstand shouldBe ProdusentModel.Kalenderavtale.Tilstand.ARBEIDSGIVER_HAR_GODTATT
                    it.startTidspunkt shouldBe LocalDateTime.parse("2024-10-12T07:20:50.52")
                    it.sluttTidspunkt shouldBe LocalDateTime.parse("2024-10-12T08:20:50.52")
                    it.lokasjon shouldBe ProdusentModel.Kalenderavtale.Lokasjon(
                        postnummer = "1234",
                        poststed = "Kneika",
                        adresse = "rundt svingen og borti høgget"
                    )
                    it.digitalt shouldBe true
                }
            }
        }




        engine.kalenderavtaleOppdaterByEksternId(merkelapp, eksternId).let { response ->
            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            lateinit var oppdatertByEksternId: MutationKalenderavtale.KalenderavtaleOppdaterVellykket
            it("respons inneholder forventet data") {
                oppdatertByEksternId =
                    response.getTypedContent<MutationKalenderavtale.KalenderavtaleOppdaterVellykket>("kalenderavtaleOppdaterByEksternId")
                oppdatertByEksternId should beOfType<MutationKalenderavtale.KalenderavtaleOppdaterVellykket>()
            }

            it("sends message to kafka") {
                kafkaProducer.hendelser.removeLast().also { hendelse ->
                    hendelse shouldBe instanceOf<HendelseModel.KalenderavtaleOppdatert>()
                    hendelse as HendelseModel.KalenderavtaleOppdatert
                    hendelse.notifikasjonId shouldBe oppdatertByEksternId.id
                }
            }

            it("updates produsent modell") {
                produsentRepository.hentNotifikasjon(oppdatertByEksternId.id).let {
                    it shouldNot beNull()
                    it should beOfType<ProdusentModel.Kalenderavtale>()

                    it as ProdusentModel.Kalenderavtale
                    it.merkelapp shouldBe merkelapp
                    it.virksomhetsnummer shouldBe "42"

                    it.tekst shouldBe "hello world"
                    it.grupperingsid shouldBe grupperingsid
                    it.lenke shouldBe "https://foo.bar"
                    it.eksternId shouldBe eksternId
                    it.tilstand shouldBe ProdusentModel.Kalenderavtale.Tilstand.ARBEIDSGIVER_HAR_GODTATT
                    it.startTidspunkt shouldBe LocalDateTime.parse("2024-10-12T07:20:50.52")
                    it.sluttTidspunkt shouldBe LocalDateTime.parse("2024-10-12T08:20:50.52")
                    it.lokasjon shouldBe ProdusentModel.Kalenderavtale.Lokasjon(
                        postnummer = "1234",
                        poststed = "Kneika",
                        adresse = "rundt svingen og borti høgget"
                    )
                    it.digitalt shouldBe true
                }
            }
        }

        // starttidspunkt etter sluttidspunkt ved opprettelse
        engine.nyKalenderavtale(
            grupperingsid = grupperingsid,
            merkelapp = merkelapp,
            eksternId = "400",
            startTidspunkt = "2024-10-12T08:20:50.52",
            sluttTidspunkt = "2024-10-12T07:20:50.52"
        ).let {
            it("status is 200 OK") {
                it.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                it.getGraphqlErrors() should beEmpty()
            }
            it("respons inneholder forventet data") {
                val valideringsfeil =
                    it.getTypedContent<Error.UgyldigKalenderavtale>("nyKalenderavtale")
                valideringsfeil should beOfType<Error.UgyldigKalenderavtale>()
            }
        }

        // starttidspunkt etter sluttidspunkt ved oppdaterById
        engine.kalenderavtaleOppdater(
            id = nyKalenderavtale.id,
            startTidspunkt = "2024-10-12T08:20:50.52",
            sluttTidspunkt = "2024-10-12T07:20:50.52",
        ).let { response ->
            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }
            it("respons inneholder forventet data") {
                val valideringsfeil =
                    response.getTypedContent<Error.UgyldigKalenderavtale>("kalenderavtaleOppdater")
                valideringsfeil should beOfType<Error.UgyldigKalenderavtale>()
            }
        }

        // starttidspunkt etter sluttidspunkt ved oppdaterByEksternId
        engine.kalenderavtaleOppdaterByEksternId(
            eksternId = eksternId,
            merkelapp = merkelapp,
            startTidspunkt = "2024-10-12T08:20:50.52",
            sluttTidspunkt = "2024-10-12T07:20:50.52",
        ).let { response ->
            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }
            it("respons inneholder forventet data") {
                val valideringsfeil =
                    response.getTypedContent<Error.UgyldigKalenderavtale>("kalenderavtaleOppdaterByEksternId")
                valideringsfeil should beOfType<Error.UgyldigKalenderavtale>()
            }
        }
    }
})

private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val kafkaProducer = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentRepository,
    )
    return Triple(produsentRepository, kafkaProducer, engine)
}


private fun TestApplicationEngine.nyKalenderavtale(
    grupperingsid: String,
    merkelapp: String,
    eksternId: String = "heu",
    startTidspunkt: String = "2024-10-12T07:20:50.52",
    sluttTidspunkt: String = "2024-10-12T08:20:50.52",
) = produsentApi(
    """
        mutation {
            nyKalenderavtale(
                mottakere: [{
                    naermesteLeder: {
                        naermesteLederFnr: "12345678910"
                        ansattFnr: "321"
                    } 
                }]
                lenke: "https://foo.bar"
                tekst: "hello world"
                merkelapp: "$merkelapp"
                grupperingsid: "$grupperingsid"
                eksternId: "$eksternId"
                startTidspunkt: "$startTidspunkt"
                sluttTidspunkt: "$sluttTidspunkt"
                lokasjon: {
                    postnummer: "1234"
                    poststed: "Kneika"
                    adresse: "rundt svingen og borti høgget"
                }
                erDigitalt: true
                virksomhetsnummer: "42"
                hardDelete: {
                  den: "2019-10-13T07:20:50.52"
                }
            ) {
                __typename
                ... on NyKalenderavtaleVellykket {
                    id
                    eksterneVarsler {
                        id
                    }
                }
                ... on Error {
                    feilmelding
                }
            }
        }
    """.trimIndent()
)

private fun TestApplicationEngine.kalenderavtaleOppdater(
    id: UUID,
    startTidspunkt: String = "2024-10-12T07:20:50.52",
    sluttTidspunkt: String = "2024-10-12T08:20:50.52",
) = produsentApi(
    """
        mutation {
            kalenderavtaleOppdater(
                id: "$id"
                nyLenke: "https://foo.bar"
                nyTekst: "hello world"
                nyTilstand: ARBEIDSGIVER_HAR_GODTATT
                nyttStartTidspunkt: "$startTidspunkt"
                nyttSluttTidspunkt: "$sluttTidspunkt"
                nyLokasjon: {
                    postnummer: "1234"
                    poststed: "Kneika"
                    adresse: "rundt svingen og borti høgget"
                }
                nyErDigitalt: true
                hardDelete: {
                  nyTid: { 
                    den: "2019-10-13T07:20:50.52" 
                    }
                  strategi: OVERSKRIV
                }
            ) {
                __typename
                ... on KalenderavtaleOppdaterVellykket {
                    id
                }
                ... on Error {
                    feilmelding
                }
            }
        }
    """.trimIndent()
)

private fun TestApplicationEngine.kalenderavtaleOppdaterByEksternId(
    merkelapp: String,
    eksternId: String,
    startTidspunkt: String = "2024-10-12T07:20:50.52",
    sluttTidspunkt: String = "2024-10-12T08:20:50.52",
) = produsentApi(
    """
        mutation {
            kalenderavtaleOppdaterByEksternId(
                merkelapp: "$merkelapp"
                eksternId: "$eksternId"
                nyTilstand: ARBEIDSGIVER_HAR_GODTATT
                nyLenke: "https://foo.bar"
                nyTekst: "hello world"
                nyttStartTidspunkt: "$startTidspunkt"
                nyttSluttTidspunkt: "$sluttTidspunkt"
                nyLokasjon: {
                    postnummer: "1234"
                    poststed: "Kneika"
                    adresse: "rundt svingen og borti høgget"
                }
                nyErDigitalt: true
                hardDelete: {
                  nyTid: { 
                    den: "2019-10-13T07:20:50.52" 
                    }
                  strategi: OVERSKRIV
                }
            ) {
                __typename
                ... on KalenderavtaleOppdaterVellykket {
                    id
                }
                ... on Error {
                    feilmelding
                }
            }
        }
    """.trimIndent()
)
