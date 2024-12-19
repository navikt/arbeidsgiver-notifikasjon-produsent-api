package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
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
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime

class NyBeskjedTests : DescribeSpec({

    describe("produsent-api happy path") {
        val (produsentRepository, kafkaProducer, engine) = setupEngine()
        val nyBeskjed = opprettOgTestNyBeskjed(engine)

        it("sends message to kafka") {
            kafkaProducer.hendelser.removeLast().also {
                it shouldBe instanceOf<BeskjedOpprettet>()
                it as BeskjedOpprettet
                it.notifikasjonId shouldBe nyBeskjed.id
                it.lenke shouldBe "https://foo.bar"
                it.tekst shouldBe "hello world"
                it.merkelapp shouldBe "tag"
                it.mottakere.single() shouldBe NærmesteLederMottaker(
                    naermesteLederFnr = "12345678910",
                    ansattFnr = "321",
                    virksomhetsnummer = "42"
                )
                it.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                it.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
            }
        }

        it("updates produsent modell") {
            val id = nyBeskjed.id
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }
    }

    describe("produsent-api happy path med grupperingsid for sak") {
        val (produsentRepository, kafkaProducer, engine) = setupEngine()
        val sakOpprettet = HendelseModel.SakOpprettet(
            virksomhetsnummer = "1",
            merkelapp = "tag",
            grupperingsid = "g42",
            mottakere = listOf(
                NærmesteLederMottaker(
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
            nesteSteg = null,
            hardDelete = null,
            tilleggsinformasjon = null
        ).also {
            produsentRepository.oppdaterModellEtterHendelse(it)
        }
        val nyBeskjed = opprettOgTestNyBeskjed(engine, """ grupperingsid: "g42" """)

        it("sends message to kafka") {
            kafkaProducer.hendelser.removeLast().also {
                it shouldBe instanceOf<BeskjedOpprettet>()
                it as BeskjedOpprettet
                it.notifikasjonId shouldBe nyBeskjed.id
                it.sakId shouldBe sakOpprettet.sakId
                it.grupperingsid shouldBe sakOpprettet.grupperingsid
                it.lenke shouldBe "https://foo.bar"
                it.tekst shouldBe "hello world"
                it.merkelapp shouldBe "tag"
                it.mottakere.single() shouldBe NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                it.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                it.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
            }
        }

        it("updates produsent modell") {
            val id = nyBeskjed.id
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }
    }

    describe("Validering av mottaker mot sak") {
        val (produsentRepository, _, engine) = setupEngine()
        val sakOpprettet = HendelseModel.SakOpprettet(
            virksomhetsnummer = "42",
            merkelapp = "tag",
            grupperingsid = "g42",
            mottakere = listOf(
                NærmesteLederMottaker(
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
            nesteSteg = null,
            hardDelete = null,
            tilleggsinformasjon = null
        ).also {
            produsentRepository.oppdaterModellEtterHendelse(it)
        }

        val nyBeskjed = opprettBeskjedMedMottaker(
            engine,
            grupperingsId = "g42",
            virksomhetsnummer = "41",
            eksternId = "1",
            mottaker =
                """naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    }"""
        )
        it("Beskjed har feil virksomhetsnummer") {
            nyBeskjed shouldBe instanceOf<Error.UgyldigMottaker>()
        }

        val nyBeskjed2 = opprettBeskjedMedMottaker(
            engine,
            grupperingsId = "g42",
            virksomhetsnummer = "42",
            eksternId = "2",
            mottaker =
                """altinn: {
                        serviceCode: "1",
                        serviceEdition: "1"
                    }"""
        )

        it("Beskjed har feil mottakerType") {
            nyBeskjed2 shouldBe instanceOf<Error.UgyldigMottaker>()
        }

        val nyBeskjed3 = opprettBeskjedMedMottaker(
            engine,
            grupperingsId = "g41",
            virksomhetsnummer = "41",
            eksternId = "3",
            mottaker =
                """altinn: {
                        serviceCode: "1",
                        serviceEdition: "1"
                    }"""
        )

        it("Beskjed har ikke grupperingsid, og er ikke koblet til sak") {
            nyBeskjed3 shouldBe instanceOf<MutationNyBeskjed.NyBeskjedVellykket>()
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


private suspend inline fun DescribeSpecContainerScope.opprettOgTestNyBeskjed(
    engine: TestApplicationEngine,
    grupperingsid: String = "",
): MutationNyBeskjed.NyBeskjedVellykket {
    val response = engine.produsentApi(
        """
        mutation {
            nyBeskjed(nyBeskjed: {
                mottaker: {
                    naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    } 
                }
                notifikasjon: {
                    lenke: "https://foo.bar",
                    tekst: "hello world",
                    merkelapp: "tag",
                }
                metadata: {
                    eksternId: "heu",
                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                    virksomhetsnummer: "42"
                    hardDelete: {
                      den: "2019-10-13T07:20:50.52"
                    }
                    $grupperingsid
                }
            }) {
                __typename
                ... on NyBeskjedVellykket {
                    id
                    eksterneVarsler {
                        id
                    }
                }
            }
        }
    """.trimIndent()
    )
    it("status is 200 OK") {
        response.status() shouldBe HttpStatusCode.OK
    }

    it("response inneholder ikke feil") {
        response.getGraphqlErrors() should beEmpty()
    }

    lateinit var nyBeskjed: MutationNyBeskjed.NyBeskjedResultat
    it("respons inneholder forventet data") {
        nyBeskjed = response.getTypedContent<MutationNyBeskjed.NyBeskjedVellykket>("nyBeskjed")
        nyBeskjed should beOfType<MutationNyBeskjed.NyBeskjedVellykket>()
    }
    return nyBeskjed as MutationNyBeskjed.NyBeskjedVellykket
}


private fun DescribeSpecContainerScope.opprettBeskjedMedMottaker(
    engine: TestApplicationEngine,
    grupperingsId: String,
    eksternId: String,
    mottaker: String,
    virksomhetsnummer: String,
): MutationNyBeskjed.NyBeskjedResultat {
    val mutation =
        """
        mutation {
            nyBeskjed(nyBeskjed: {
                mottakere: [{
                    $mottaker
                    }
                ]
                
                notifikasjon: {
                    lenke: "https://foo.bar",
                    tekst: "hello world",
                    merkelapp: "tag",
                }
                metadata: {
                    eksternId: "$eksternId",
                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                    virksomhetsnummer: "$virksomhetsnummer"
                    hardDelete: {
                      den: "2019-10-13T07:20:50.52"
                    }
                    grupperingsid: "$grupperingsId",
                }
            }) {
                __typename
                ... on NyBeskjedVellykket {
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

    val response = engine.produsentApi(mutation)
    response.status() shouldBe HttpStatusCode.OK
    response.getGraphqlErrors() should beEmpty()
    return response.getTypedContent("nyBeskjed")
}