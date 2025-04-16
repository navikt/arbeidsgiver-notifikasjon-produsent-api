package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HardDeleteSakTest {

    val virksomhetsnummer = "123"
    private val sakId = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    private val sakId2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
    private val merkelapp = "tag"
    private val grupperingsid = "123"
    private val grupperingsid2 = "234"
    private val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    private val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

    private val sakOpprettet = SakOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        grupperingsid = grupperingsid,
        mottakere = listOf(mottaker),
        hendelseId = sakId,
        sakId = sakId,
        tittel = "test",
        lenke = "https://nav.no",
        oppgittTidspunkt = opprettetTidspunkt,
        mottattTidspunkt = opprettetTidspunkt,
        kildeAppNavn = "",
        produsentId = "",
        nesteSteg = null,
        hardDelete = null,
        tilleggsinformasjon = null
    )
    private val beskjedOpprettet = BeskjedOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        grupperingsid = grupperingsid,
        mottakere = listOf(mottaker),
        hendelseId = uuid("11"),
        notifikasjonId = uuid("11"),
        sakId = sakId,
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = opprettetTidspunkt,
        kildeAppNavn = "",
        produsentId = "",
        hardDelete = null,
        eksternId = "11",
        eksterneVarsler = emptyList(),
    )
    private val sakOpprettet2 = sakOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = sakId2,
        sakId = sakId2,
    )
    private val beskedOpprettet2 = beskjedOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = uuid("12"),
        notifikasjonId = uuid("12"),
        sakId = sakId2,
        eksternId = "12",
    )

    @Test
    fun `HardDelete-oppførsel Sak Eksisterende sak blir slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val kafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)
            produsentModel.oppdaterModellEtterHendelse(beskedOpprettet2)

            with(client.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$sakId") {
                        __typename
                        ... on HardDeleteSakVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer tilbake id-en
                val vellykket = getTypedContent<MutationHardDeleteSak.HardDeleteSakVellykket>("hardDeleteSak")
                assertEquals(sakId, vellykket.id)
            }


            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as HardDelete
            }

            // sak1 har blitt fjernet fra modellen
            assertNull(produsentModel.hentSak(sakId))
            // beskjed1 har blitt fjernet fra modellen
            assertNull(produsentModel.hentNotifikasjon(beskjedOpprettet.notifikasjonId))
            // sak2 har ikke blitt fjernet fra modellen
            assertNotNull(produsentModel.hentSak(sakId2))
            // beskjed2 har ikke blitt fjernet fra modellen
            assertNotNull(produsentModel.hentNotifikasjon(beskedOpprettet2.notifikasjonId))
        }
    }

    @Test
    fun `HardDelete-oppførsel Sak mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$sakId") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSak")
        }
    }

    @Test
    fun `HardDelete-oppførsel Sak med feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteSak(id: "$sakId") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.UgyldigMerkelapp>("hardDeleteSak")
        }
    }

    @Test
    fun `hardDeleteSakByEksternId-oppførsel Eksisterende sak blir slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        val kafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            kafkaProducer.clear()
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)

            with(
                client.produsentApi(
                    """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on HardDeleteSakVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
                )
            ) {
                // returnerer tilbake id-en
                val vellykket =
                    getTypedContent<MutationHardDeleteSak.HardDeleteSakVellykket>("hardDeleteSakByGrupperingsid")
                assertEquals(sakId, vellykket.id)
            }


            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as HardDelete
            }

            // finnes ikke i modellen
            assertNull(produsentModel.hentSak(sakId))
            // sak2 finnes fortsatt i modellen
            assertNotNull(produsentModel.hentSak(sakId2))

            // opprettelse av ny sak med samme merkelapp og grupperingsid feiler
            client.produsentApi(
                """
                    mutation {
                        nySak(
                            virksomhetsnummer: "1"
                            merkelapp: "$merkelapp"
                            grupperingsid: "$grupperingsid"
                            mottakere: [{
                                altinn: {
                                    serviceCode: "5441"
                                    serviceEdition: "1"
                                }
                            }]
                            initiellStatus: MOTTATT
                            tidspunkt: "2020-01-01T01:01Z"
                            tittel: "ny sak"
                            tilleggsinformasjon: "Her er noe tilleggsinformasjon"
                            lenke: "#foo"
                        ) {
                            __typename
                            ... on NySakVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
            ).getTypedContent<Error.DuplikatGrupperingsidEtterDelete>("nySak")
        }
    }

    @Test
    fun `hardDeleteSakByEksternId-oppførsel Sak mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSakByGrupperingsid")
        }
    }

    @Test
    fun `hardDeleteSakByEksternId-oppførsel Sak med feil merkelapp men riktig eksternId`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSakByGrupperingsid")
        }
    }

    @Test
    fun `hardDeleteSakByEksternId-oppførsel Sak med feil grupperingsid men riktig merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            val response = client.produsentApi(
                """
                mutation {
                    hardDeleteSakByGrupperingsid(grupperingsid: "nope$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            // returnerer feilmelding
            response.getTypedContent<Error.SakFinnesIkke>("hardDeleteSakByGrupperingsid")
        }
    }
}

