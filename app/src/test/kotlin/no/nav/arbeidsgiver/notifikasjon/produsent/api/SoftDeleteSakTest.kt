package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class SoftDeleteSakTest {

    private val virksomhetsnummer = "123"
    private val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    private val uuid2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
    private val uuid3 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cb")
    private val uuid4 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cc")
    private val uuid5 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ce")
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
        hendelseId = uuid,
        sakId = uuid,
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
    private val sakOpprettet2 = sakOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = uuid2,
        sakId = uuid2,
    )

    private val oppgaveOpprettet = OppgaveOpprettet(
        eksternId = "1",
        grupperingsid = grupperingsid,
        merkelapp = merkelapp,
        frist = null,
        tekst = "test",
        sakId = uuid,
        opprettetTidspunkt = opprettetTidspunkt,
        eksterneVarsler = listOf(),
        mottakere = listOf(mottaker),
        hardDelete = null,
        hendelseId = UUID.randomUUID(),
        kildeAppNavn = "",
        lenke = "https://nav.no",
        produsentId = "",
        notifikasjonId = uuid3,
        virksomhetsnummer = "1",
        påminnelse = null,
    )

    private val oppgaveOpprettet2 = oppgaveOpprettet.copy(
        grupperingsid = grupperingsid2,
        hendelseId = uuid4,
        notifikasjonId = uuid4,
        eksternId = "2",
    )

    private val beskjedOpprettet = HendelseModel.BeskjedOpprettet(
        eksternId = "3",
        grupperingsid = grupperingsid,
        merkelapp = merkelapp,
        tekst = "test",
        opprettetTidspunkt = opprettetTidspunkt,
        eksterneVarsler = listOf(),
        mottakere = listOf(mottaker),
        hardDelete = null,
        hendelseId = UUID.randomUUID(),
        kildeAppNavn = "",
        lenke = "https://nav.no",
        produsentId = "",
        notifikasjonId = uuid5,
        virksomhetsnummer = "1",
        sakId = uuid,
    )

    @Test
    fun `SoftDelete Eksisterende sak blir markert som slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)

            with(
                client.produsentApi(
                    """
                mutation {
                    softDeleteSak(id: "$uuid") {
                        __typename
                        ... on SoftDeleteSakVellykket {
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
                    getTypedContent<MutationSoftDeleteSak.SoftDeleteSakVellykket>("softDeleteSak")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as SoftDelete
            }

            // har blitt slettet i modellen
            assertNull(produsentModel.hentSak(uuid))

            // notifikasjon2 har ikke slettet-status i modellen
            produsentModel.hentSak(uuid2).let {
                assertNotNull(it)
                assertNull(it.deletedAt)
            }
        }
    }

    @Test
    fun `SoftDelete Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            with(client.produsentApi(
                """
                mutation {
                    softDeleteSak(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.SakFinnesIkke>("softDeleteSak")
            }
        }
    }

    @Test
    fun `SoftDelete Oppgave med feil merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet.copy(merkelapp = "feil merkelapp"))

            with(client.produsentApi(
                """
                mutation {
                    softDeleteSak(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.UgyldigMerkelapp>("softDeleteSak")
            }
        }
    }

    @Test
    fun `SoftDelete SoftDelete cascader fra sak til notifikasjoner`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)
            produsentModel.oppdaterModellEtterHendelse(beskjedOpprettet)


            with(client.produsentApi(
                """
                mutation {
                    softDeleteSak(id: "$uuid") {
                        __typename
                        ... on SoftDeleteSakVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // sak er slettet
                val vellykket =
                    getTypedContent<MutationSoftDeleteSak.SoftDeleteSakVellykket>("softDeleteSak")
                assertEquals(uuid, vellykket.id)
            }


            // oppgave er slettet
            assertNull(produsentModel.hentNotifikasjon(uuid3))

            // oppgave 2 er ikke slettet
            produsentModel.hentNotifikasjon(uuid4).let {
                assertNotNull(it)
                assertNull(it.deletedAt)
            }

            // beskjed er slettet
            assertNull(produsentModel.hentNotifikasjon(uuid5))
        }
    }

    @Test
    fun `SoftDeleteSakByGrupperingsid Eksisterende oppgave blir markert som slettet`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet2)

            with(client.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on SoftDeleteSakVellykket {
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
                val vellykket =
                    getTypedContent<MutationSoftDeleteSak.SoftDeleteSakVellykket>("softDeleteSakByGrupperingsid")
                assertEquals(uuid, vellykket.id)
            }

            // har sendt melding til kafka
            kafkaProducer.hendelser.removeLast().also {
                it as SoftDelete
            }

            // har blitt slettet i modellen
            assertNull(produsentModel.hentSak(uuid))

            // oppgave 2 har ikke fått slettet tidspunkt
            produsentModel.hentSak(uuid2).let {
                assertNotNull(it)
                assertNull(it.deletedAt)
            }
        }
    }

    @Test
    fun `SoftDeleteSakByGrupperingsid Oppgave mangler`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = ProdusentRepositoryImpl(database)
        ) {
            with(client.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.SakFinnesIkke>("softDeleteSakByGrupperingsid")
            }
        }
    }

    @Test
    fun `SoftDeleteSakByGrupperingsid Oppgave med feil merkelapp men riktig grupperingsid`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            with(client.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "$grupperingsid", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.SakFinnesIkke>("softDeleteSakByGrupperingsid")
            }
        }
    }

    @Test
    fun `SoftDeleteSakByGrupperingsid Oppgave med feil grupperingsid men riktig merkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentModel
        ) {
            produsentModel.oppdaterModellEtterHendelse(sakOpprettet)

            with(client.produsentApi(
                """
                mutation {
                    softDeleteSakByGrupperingsid(grupperingsid: "nope$grupperingsid", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )) {
                // returnerer feilmelding
                getTypedContent<Error.SakFinnesIkke>("softDeleteSakByGrupperingsid")
            }
        }
    }
}

