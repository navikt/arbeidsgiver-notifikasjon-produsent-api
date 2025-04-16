package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class NesteStegTest {
    @Test
    fun `Oppretter ny sak med neste steg`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        val hendelseProdusent = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = hendelseProdusent,
            produsentRepository = produsentRepository,
        ) {
            val sakUtenNesteSteg = client.nySak(uuid("1").toString())
            val sakMedNesteSteg = client.nySak(uuid("2").toString(), "foo")

            val sakUtenNesteStegID = sakUtenNesteSteg.getTypedContent<UUID>("$.nySak.id")
            val sakMedNesteStegID = sakMedNesteSteg.getTypedContent<UUID>("$.nySak.id")

            // should be successfull
            assertEquals("NySakVellykket", sakUtenNesteSteg.getTypedContent<String>("$.nySak.__typename"))
            assertEquals("NySakVellykket", sakMedNesteSteg.getTypedContent<String>("$.nySak.__typename"))

            // should not have neste steg
            with(produsentRepository.hentSak(sakUtenNesteStegID)) {
                assertNotNull(this)
                assertNull(nesteSteg)
            }

            // should have neste steg
            with(produsentRepository.hentSak(sakMedNesteStegID)!!) {
                assertNotNull(this)
                assertNotNull(nesteSteg)
                assertEquals("foo", nesteSteg)
            }
        }
    }

    @Test
    fun `Endrer nesteSteg på eksisterende sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        val hendelseProdusent = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = hendelseProdusent,
            produsentRepository = produsentRepository,
        ) {
            val sak = client.nySak(uuid("1").toString(), null)
            val sakID = sak.getTypedContent<UUID>("$.nySak.id")
            val idempotencyKey1 = uuid("2").toString()
            val idempotencyKey2 = uuid("3").toString()

            val nesteSteg1 = client.endreNesteSteg(sakID, "foo", idempotencyKey1)

            // Endrer neste steg med ny idempontency key
            assertEquals("NesteStegSakVellykket", nesteSteg1.getTypedContent<String>("$.nesteStegSak.__typename"))
            var hentetSak = produsentRepository.hentSak(sakID)!!
            assertEquals("foo", hentetSak.nesteSteg)

            // Forsøker endre neste steg med samme idempontency key og forventer ingen endring
            client.endreNesteSteg(sakID, "bar", idempotencyKey1)
            hentetSak = produsentRepository.hentSak(sakID)!!
            assertEquals("foo", hentetSak.nesteSteg)
            // Endrere med ny idempontency key og forventer endring
            val nesteSteg2 = client.endreNesteSteg(sakID, "baz", idempotencyKey2)
            assertEquals("NesteStegSakVellykket", nesteSteg2.getTypedContent<String>("$.nesteStegSak.__typename"))
            hentetSak = produsentRepository.hentSak(sakID)!!
            assertEquals("baz", hentetSak.nesteSteg)
            // Endrer neste steg til null
            val nesteSteg3 = client.endreNesteSteg(sakID, null, uuid("4").toString())
            assertEquals("NesteStegSakVellykket", nesteSteg3.getTypedContent<String>("$.nesteStegSak.__typename"))
            hentetSak = produsentRepository.hentSak(sakID)!!
            assertEquals(null, hentetSak.nesteSteg)
            // Endrer neste steg uten idempontency key
            val nesteSteg4 = client.endreNesteSteg(sakID, "foo", null)
            assertEquals("NesteStegSakVellykket", nesteSteg4.getTypedContent<String>("$.nesteStegSak.__typename"))
            hentetSak = produsentRepository.hentSak(sakID)!!
            assertEquals("foo", hentetSak.nesteSteg)
            // Endrer til null uten idempontency key
            val nesteSteg5 = client.endreNesteSteg(sakID, null, null)
            assertEquals("NesteStegSakVellykket", nesteSteg5.getTypedContent<String>("$.nesteStegSak.__typename"))
            hentetSak = produsentRepository.hentSak(sakID)!!
            assertEquals(null, hentetSak.nesteSteg)
        }
    }
}


private suspend fun HttpClient.nySak(
    grupperingsid: String,
    nesteSteg: String? = null,
) =
    produsentApi(
        """
            mutation {
                nySak(
                    virksomhetsnummer: "1"
                    merkelapp: "tag"
                    grupperingsid: "$grupperingsid"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    initiellStatus: ${SaksStatus.MOTTATT}
                    tittel: "Foo"
                    tilleggsinformasjon: "Her er noe tilleggsinformasjon"
                    ${if (nesteSteg == null) "" else "nesteSteg: \"$nesteSteg\""}
                    lenke: null
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )


private suspend fun HttpClient.endreNesteSteg(
    id: UUID,
    nesteSteg: String?,
    idempotencyKey: String?,
) =
    produsentApi(
        """
            mutation {
                nesteStegSak(
                    id: "$id"
                    ${if (nesteSteg == null) "" else "nesteSteg: \"$nesteSteg\""}
                    ${if (idempotencyKey == null) "" else "idempotencyKey: \"$idempotencyKey\""}
                ) {
                    __typename
                    ... on NesteStegSakVellykket {
                        id
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            }
        """
    )

