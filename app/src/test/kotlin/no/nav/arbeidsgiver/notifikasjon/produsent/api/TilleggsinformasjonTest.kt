package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*
import kotlin.test.*

class TilleggsinformasjonTest {
    @Test
    fun `Oppretter ny sak med tilleggsinformasjon`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentRepository,
        ) {
            val sakUtenTilleggsinformasjon = client.nySak(uuid("1").toString())
            val sakMedTilleggsinformasjon = client.nySak(uuid("2").toString(), "foo")

            val sakUtenTilleggsinformasjonID = sakUtenTilleggsinformasjon.getTypedContent<UUID>("$.nySak.id")
            val sakMedTilleggsinformasjonID = sakMedTilleggsinformasjon.getTypedContent<UUID>("$.nySak.id")

            // should be successfull
            assertEquals("NySakVellykket", sakUtenTilleggsinformasjon.getTypedContent<String>("$.nySak.__typename"))
            assertEquals("NySakVellykket", sakMedTilleggsinformasjon.getTypedContent<String>("$.nySak.__typename"))

            // should not have tilleggsinformasjon
            produsentRepository.hentSak(sakUtenTilleggsinformasjonID).let {
                assertNotNull(it)
                assertEquals(null, it.tilleggsinformasjon)
            }

            // should have tilleggsinformasjon
            produsentRepository.hentSak(sakMedTilleggsinformasjonID).let {
                assertNotNull(it)
                assertEquals("foo", it.tilleggsinformasjon)
            }
        }
    }

    @Test
    fun `Endrer tilleggsinformasjon på eksisterende sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentRepository,
        ) {
            val sak = client.nySak(uuid("1").toString(), null)
            val sakID = sak.getTypedContent<UUID>("$.nySak.id")
            val idempotencyKey1 = uuid("2").toString()
            val idempotencyKey2 = uuid("3").toString()

            client.endreTilleggsinformasjonOgForventSuksess(sakID, "foo", idempotencyKey1)

            // Endrer tilleggsinformasjon med ny idempontency key
            produsentRepository.hentSak(sakID).let {
                assertNotNull(it)
                assertEquals("foo", it.tilleggsinformasjon)
            }

            // Forsøker endre tilleggsinformasjon med samme idempontency key og forventer ingen endring
            client.endreTilleggsinformasjonOgForventSuksess(sakID, "bar", idempotencyKey1)
            produsentRepository.hentSak(sakID).let {
                assertNotNull(it)
                assertEquals("foo", it.tilleggsinformasjon)
            }

            // Endrere med ny idempontency key og forventer endring
            client.endreTilleggsinformasjonOgForventSuksess(sakID, "baz", idempotencyKey2)
            produsentRepository.hentSak(sakID).let {
                assertNotNull(it)
                assertEquals("baz", it.tilleggsinformasjon)
            }

            // Endrer tilleggsinformasjon til null
            client.endreTilleggsinformasjonOgForventSuksess(sakID, null, uuid("4").toString())
            produsentRepository.hentSak(sakID).let {
                assertNotNull(it)
                assertEquals(null, it.tilleggsinformasjon)
            }

            // Endrer tilleggsinformasjon uten idempontency key
            client.endreTilleggsinformasjonOgForventSuksess(sakID, "foo", null)
            produsentRepository.hentSak(sakID).let {
                assertNotNull(it)
                assertEquals("foo", it.tilleggsinformasjon)
            }

            // Endrer til null uten idempontency key
            client.endreTilleggsinformasjonOgForventSuksess(sakID, null, null)
            produsentRepository.hentSak(sakID).let {
                assertNotNull(it)
                assertNull(it.tilleggsinformasjon)
            }
        }
    }
}


private suspend fun HttpClient.nySak(
    grupperingsid: String,
    tilleggsinformasjon: String? = null,
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
                    ${if (tilleggsinformasjon == null) "" else "tilleggsinformasjon: \"$tilleggsinformasjon\""}
                    lenke: ${null}
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )


private suspend fun HttpClient.endreTilleggsinformasjonOgForventSuksess(
    id: UUID,
    tilleggsinformasjon: String?,
    idempotencyKey: String?,
) =
    produsentApi(
        """
            mutation {
                tilleggsinformasjonSak(
                    id: "$id"
                    ${if (tilleggsinformasjon == null) "" else "tilleggsinformasjon: \"$tilleggsinformasjon\""}
                    ${if (idempotencyKey == null) "" else "idempotencyKey: \"$idempotencyKey\""}
                ) {
                    __typename
                    ... on TilleggsinformasjonSakVellykket {
                        id
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            }
        """
    ).also {
        it.getTypedContent<MutationTilleggsinformasjonSak.TilleggsinformasjonSakVellykket>("$.tilleggsinformasjonSak")
    }

