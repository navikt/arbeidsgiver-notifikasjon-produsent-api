package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*

private val sakOpprettet = HendelseModel.SakOpprettet(
    hendelseId = uuid("1"),
    virksomhetsnummer = "1",
    produsentId = "1",
    kildeAppNavn = "test",
    sakId = uuid("1"),
    grupperingsid = "grupperingsid",
    merkelapp = "tag",
    mottakere = listOf(
        HendelseModel.AltinnMottaker(
            serviceCode = "5441",
            serviceEdition = "1",
            virksomhetsnummer = "1"
        )
    ),
    tittel = "foo",
    lenke = "#foo",
    oppgittTidspunkt = null,
    mottattTidspunkt = OffsetDateTime.now(),
    hardDelete = null,
)

class NySakDuplisertTests : DescribeSpec({

    describe("opprett nySak to ganger med samme input") {
        val (produsentRepository, hendelseProdusent, engine) = setupEngine()
        val response1 = engine.nySak()
        it("should be successfull") {
            response1.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        val id1 = response1.getTypedContent<UUID>("$.nySak.id")

        val response2 = engine.nySak()
        it("opprett enda en sak should be successfull") {
            response2.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        val id2 = response2.getTypedContent<UUID>("$.nySak.id")

        it("samme id") {
            id1 shouldBe id2
        }

        it("det er to hendelser med samme sakId i kafka") {
            hendelseProdusent.hendelser shouldHaveSize 2
            hendelseProdusent.hendelser[0].aggregateId shouldBe id1
            hendelseProdusent.hendelser[1].aggregateId shouldBe id1
        }
    }

    describe("opprett ny sak og det finnes en delvis feilet saksopprettelse") {
        val (produsentRepository, hendelseProdusent, engine) = setupEngine()
        hendelseProdusent.clear()
        hendelseProdusent.hendelser.add(sakOpprettet)
        produsentRepository.oppdaterModellEtterHendelse(sakOpprettet)

        val response1 = engine.nySak()
        it("should be successfull") {
            response1.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }
        val id1 = response1.getTypedContent<UUID>("$.nySak.id")
        it("opprinnelig sakId returneres") {
            id1 shouldBe sakOpprettet.sakId
        }
        it("statushendelsen i kafka har opprinelig sakId") {
            hendelseProdusent.hendelser shouldHaveSize 2
            hendelseProdusent.hendelser[0].aggregateId shouldBe id1
            hendelseProdusent.hendelser[1].aggregateId shouldBe id1
            hendelseProdusent.hendelser[0] should beInstanceOf<HendelseModel.SakOpprettet>()
            hendelseProdusent.hendelser[1] should beInstanceOf<HendelseModel.NyStatusSak>()
        }
    }
})

private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val hendelseProdusent = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = hendelseProdusent,
        produsentRepository = produsentRepository,
    )
    return Triple(produsentRepository, hendelseProdusent, engine)
}


private fun TestApplicationEngine.nySak(
    status: SaksStatus = SaksStatus.MOTTATT,
) =
    produsentApi(
        """
            mutation {
                nySak(
                    virksomhetsnummer: "${sakOpprettet.virksomhetsnummer}"
                    merkelapp: "${sakOpprettet.merkelapp}"
                    grupperingsid: "${sakOpprettet.grupperingsid}"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    initiellStatus: $status
                    tittel: "${sakOpprettet.tittel}"
                    lenke: "${sakOpprettet.lenke}"
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )