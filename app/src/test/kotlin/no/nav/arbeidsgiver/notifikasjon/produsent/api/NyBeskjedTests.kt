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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime

@ExperimentalTime
class NyBeskjedTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepository(database)
    val kafkaProducer = mockk<HendelseProdusent>()
    coEvery { kafkaProducer.sendOgHentMetadata(any<BeskjedOpprettet>()) } returns HendelseModel.HendelseMetadata(Instant.parse("1970-01-01T00:00:00Z"))

    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentRepository,
    )

    describe("produsent-api happy path") {
        val nyBeskjed = opprettOgTestNyBeskjed(engine)

        it("sends message to kafka") {
            coVerify {
                kafkaProducer.sendOgHentMetadata(withArg { beskjedOpprettet: BeskjedOpprettet ->
                    beskjedOpprettet.notifikasjonId shouldBe nyBeskjed.id
                    beskjedOpprettet.lenke shouldBe "https://foo.bar"
                    beskjedOpprettet.tekst shouldBe "hello world"
                    beskjedOpprettet.merkelapp shouldBe "tag"
                    beskjedOpprettet.mottakere.single() shouldBe NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                    beskjedOpprettet.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                    beskjedOpprettet.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
                })
            }
        }

        it("updates produsent modell") {
            val id = nyBeskjed.id
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }
    }

    describe("produsent-api happy path med grupperingsid for sak") {
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
            hardDelete = null,
        ).also {
            produsentRepository.oppdaterModellEtterHendelse(it)
        }
        val nyBeskjed = opprettOgTestNyBeskjed(engine, """ grupperingsid: "g42" """)

        it("sends message to kafka") {
            coVerify {
                kafkaProducer.sendOgHentMetadata(withArg { beskjedOpprettet: BeskjedOpprettet ->
                    beskjedOpprettet.notifikasjonId shouldBe nyBeskjed.id
                    beskjedOpprettet.sakId shouldBe sakOpprettet.sakId
                    beskjedOpprettet.grupperingsid shouldBe sakOpprettet.grupperingsid
                    beskjedOpprettet.lenke shouldBe "https://foo.bar"
                    beskjedOpprettet.tekst shouldBe "hello world"
                    beskjedOpprettet.merkelapp shouldBe "tag"
                    beskjedOpprettet.mottakere.single() shouldBe NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                    beskjedOpprettet.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                    beskjedOpprettet.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
                })
            }
        }

        it("updates produsent modell") {
            val id = nyBeskjed.id
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }
    }
})


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

