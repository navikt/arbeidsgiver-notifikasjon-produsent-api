package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

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
    nesteSteg = null,
    hardDelete = null,
    tilleggsinformasjon = null
)

class NySakDuplisertTest {

    @Test
    fun `opprett nySak to ganger med samme input`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val hendelseProdusent = FakeHendelseProdusent()
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = hendelseProdusent,
            produsentRepository = produsentRepository,
        ) {
            val response1 = client.nySak()
            // should be successfull
            assertEquals("NySakVellykket", response1.getTypedContent<String>("$.nySak.__typename"))
            val id1 = response1.getTypedContent<UUID>("$.nySak.id")

            val response2 = client.nySak()
            // opprett enda en sak should be successfull
            assertEquals("NySakVellykket", response2.getTypedContent<String>("$.nySak.__typename"))
            val id2 = response2.getTypedContent<UUID>("$.nySak.id")

            // samme id
            assertEquals(id2, id1)

            // det er to hendelser med samme sakId i kafka
            assertEquals(2, hendelseProdusent.hendelser.size)
            assertEquals(id1, hendelseProdusent.hendelser[0].aggregateId)
            assertEquals(id1, hendelseProdusent.hendelser[1].aggregateId)
        }
    }

    @Test
    fun `opprett ny sak og det finnes en delvis feilet saksopprettelse`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val hendelseProdusent = FakeHendelseProdusent()
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = hendelseProdusent,
            produsentRepository = produsentRepository,
        ) {
            hendelseProdusent.clear()
            hendelseProdusent.hendelser.add(sakOpprettet)
            produsentRepository.oppdaterModellEtterHendelse(sakOpprettet)

            val response1 = client.nySak()
            // should be successfull
            assertEquals("NySakVellykket", response1.getTypedContent<String>("$.nySak.__typename"))
            val id1 = response1.getTypedContent<UUID>("$.nySak.id")
            // opprinnelig sakId returneres
            assertEquals(sakOpprettet.sakId, id1)
            // statushendelsen i kafka har opprinelig sakId
            assertEquals(2, hendelseProdusent.hendelser.size)
            assertEquals(id1, hendelseProdusent.hendelser[0].aggregateId)
            assertEquals(id1, hendelseProdusent.hendelser[1].aggregateId)
            hendelseProdusent.hendelser[0] as HendelseModel.SakOpprettet
            hendelseProdusent.hendelser[1] as HendelseModel.NyStatusSak
        }
    }
}

private suspend fun HttpClient.nySak(
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