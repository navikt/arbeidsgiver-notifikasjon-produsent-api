package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDeleteSakOppdatert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkedulerHardDeleteSakTest {
    private val sakId = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    private val merkelapp = "tag"
    private val grupperingsid = "123"
    private val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

    private val sakOpprettet = SakOpprettet(
        virksomhetsnummer = "1",
        merkelapp = merkelapp,
        grupperingsid = grupperingsid,
        mottakere = listOf(AltinnMottaker(virksomhetsnummer = "1", serviceCode = "1", serviceEdition = "1")),
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

    private val selection = """
        __typename
        ... on SkedulerHardDeleteSakVellykket {
            id
        }
        ... on Error {
            feilmelding
        }
    """

    @Test
    fun `skedulerHardDeleteSak sender HardDeleteSakOppdatert og sletter ikke saken`() =
        withTestDatabase(Produsent.databaseConfig) { database ->
            val produsentModel = ProdusentRepositoryImpl(database)
            val kafkaProducer = FakeHendelseProdusent()
            ktorProdusentTestServer(kafkaProducer = kafkaProducer, produsentRepository = produsentModel) {
                produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

                val vellykket = client.produsentApi(
                    """
                    mutation {
                        skedulerHardDeleteSak(
                            id: "$sakId"
                            hardDelete: {
                                nyTid: { den: "2030-01-01T12:00" }
                                strategi: OVERSKRIV
                            }
                        ) { $selection }
                    }
                    """.trimIndent()
                ).getTypedContent<MutationSkedulerHardDeleteSak.SkedulerHardDeleteSakVellykket>("skedulerHardDeleteSak")
                assertEquals(sakId, vellykket.id)

                val hendelse = kafkaProducer.hendelser.removeLast() as HardDeleteSakOppdatert
                assertEquals(sakId, hendelse.sakId)
                assertEquals(merkelapp, hendelse.merkelapp)
                assertEquals(grupperingsid, hendelse.grupperingsid)
                assertEquals(
                    HendelseModel.HardDeleteUpdate(
                        nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(
                            LocalDateTime.parse("2030-01-01T12:00")
                        ),
                        strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
                    ),
                    hendelse.hardDelete
                )
                assertTrue(kafkaProducer.hendelser.none { it is HardDelete })
                assertNotNull(produsentModel.hentSak(sakId))
            }
        }

    @Test
    fun `skedulerHardDeleteSakByGrupperingsid sender HardDeleteSakOppdatert og sletter ikke saken`() =
        withTestDatabase(Produsent.databaseConfig) { database ->
            val produsentModel = ProdusentRepositoryImpl(database)
            val kafkaProducer = FakeHendelseProdusent()
            ktorProdusentTestServer(kafkaProducer = kafkaProducer, produsentRepository = produsentModel) {
                produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

                val vellykket = client.produsentApi(
                    """
                    mutation {
                        skedulerHardDeleteSakByGrupperingsid(
                            grupperingsid: "$grupperingsid"
                            merkelapp: "$merkelapp"
                            hardDelete: {
                                nyTid: { om: "P1Y" }
                                strategi: FORLENG
                            }
                        ) { $selection }
                    }
                    """.trimIndent()
                ).getTypedContent<MutationSkedulerHardDeleteSak.SkedulerHardDeleteSakVellykket>(
                    "skedulerHardDeleteSakByGrupperingsid"
                )
                assertEquals(sakId, vellykket.id)

                val hendelse = kafkaProducer.hendelser.removeLast() as HardDeleteSakOppdatert
                assertEquals(sakId, hendelse.sakId)
                assertEquals(HendelseModel.NyTidStrategi.FORLENG, hendelse.hardDelete.strategi)
                assertTrue(kafkaProducer.hendelser.none { it is HardDelete })
                assertNotNull(produsentModel.hentSak(sakId))
            }
        }

    @Test
    fun `skedulerHardDeleteSak sak mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            client.produsentApi(
                """
                mutation {
                    skedulerHardDeleteSak(
                        id: "$sakId"
                        hardDelete: { nyTid: { om: "P1Y" }, strategi: OVERSKRIV }
                    ) { $selection }
                }
                """.trimIndent()
            ).getTypedContent<Error.SakFinnesIkke>("skedulerHardDeleteSak")
        }
    }

    @Test
    fun `skedulerHardDeleteSakByGrupperingsid sak mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            client.produsentApi(
                """
                mutation {
                    skedulerHardDeleteSakByGrupperingsid(
                        grupperingsid: "$grupperingsid"
                        merkelapp: "$merkelapp"
                        hardDelete: { nyTid: { om: "P1Y" }, strategi: OVERSKRIV }
                    ) { $selection }
                }
                """.trimIndent()
            ).getTypedContent<Error.SakFinnesIkke>("skedulerHardDeleteSakByGrupperingsid")
        }
    }

    @Test
    fun `skedulerHardDeleteSak feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet.copy(merkelapp = "feil merkelapp"))

            client.produsentApi(
                """
                mutation {
                    skedulerHardDeleteSak(
                        id: "$sakId"
                        hardDelete: { nyTid: { om: "P1Y" }, strategi: OVERSKRIV }
                    ) { $selection }
                }
                """.trimIndent()
            ).getTypedContent<Error.UgyldigMerkelapp>("skedulerHardDeleteSak")
        }
    }
}
