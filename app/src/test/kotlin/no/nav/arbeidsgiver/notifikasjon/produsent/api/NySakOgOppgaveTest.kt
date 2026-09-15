package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import io.ktor.client.statement.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class NySakOgOppgaveTest {

    @Test
    fun `happy path - oppretter sak og oppgave atomisk`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val resultat = client.nySakOgOppgave()
                .nySakOgOppgaveResultat<MutationNySakOgOppgave.NySakOgOppgaveVellykket>()

            assertEquals(3, kafkaProducer.hendelser.size)
            val sakHendelse = kafkaProducer.hendelser[0] as HendelseModel.SakOpprettet
            kafkaProducer.hendelser[1] as HendelseModel.NyStatusSak
            val oppgaveHendelse = kafkaProducer.hendelser[2] as HendelseModel.OppgaveOpprettet

            assertEquals(resultat.sakId, oppgaveHendelse.sakId)
            assertEquals(resultat.oppgaveId, oppgaveHendelse.notifikasjonId)
            assertEquals("her er noe tilleggsinformasjon", sakHendelse.tilleggsinformasjon)
            assertNotNull(produsentRepository.hentSak(resultat.sakId))
            assertNotNull(produsentRepository.hentNotifikasjon(resultat.oppgaveId))
        }
    }

    @Test
    fun `idempotent - samme kall to ganger returnerer samme sak- og oppgave-id`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            val r1 = client.nySakOgOppgave().nySakOgOppgaveResultat<MutationNySakOgOppgave.NySakOgOppgaveVellykket>()
            val r2 = client.nySakOgOppgave().nySakOgOppgaveResultat<MutationNySakOgOppgave.NySakOgOppgaveVellykket>()

            assertEquals(r1.sakId, r2.sakId)
            assertEquals(r1.oppgaveId, r2.oppgaveId)
        }
    }

    @Test
    fun `sak er duplikat men oppgave finnes ikke - oppretter oppgave mot eksisterende sak`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val r1 = client.nySakOgOppgave(oppgaveEksternId = "oppgave-1")
                .nySakOgOppgaveResultat<MutationNySakOgOppgave.NySakOgOppgaveVellykket>()

            kafkaProducer.clear()

            val r2 = client.nySakOgOppgave(oppgaveEksternId = "oppgave-2")
                .nySakOgOppgaveResultat<MutationNySakOgOppgave.NySakOgOppgaveVellykket>()

            assertEquals(r1.sakId, r2.sakId)
            assertNotEquals(r1.oppgaveId, r2.oppgaveId)

            // kun OppgaveOpprettet til kafka (ingen ny SakOpprettet)
            assertEquals(1, kafkaProducer.hendelser.size)
            val oppgaveHendelse = kafkaProducer.hendelser[0] as HendelseModel.OppgaveOpprettet
            assertEquals(r1.sakId, oppgaveHendelse.sakId)
        }
    }

    @Test
    fun `sak er duplikat og oppgave er konflikt - returnerer DuplikatEksternIdOgMerkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            client.nySakOgOppgave()

            client.nySakOgOppgave(oppgaveTekst = "annen tekst")
                .nySakOgOppgaveResultat<Error.DuplikatEksternIdOgMerkelapp>()
        }
    }

    @Test
    fun `sak eksisterer og er ikke duplikat - returnerer DuplikatGrupperingsid`() = withTestDatabase(Produsent.databaseConfig) { database ->
        ktorProdusentTestServer(
            produsentRepository = ProdusentRepositoryImpl(database),
        ) {
            client.nySakOgOppgave()

            client.nySakOgOppgave(sakTittel = "annen tittel")
                .nySakOgOppgaveResultat<Error.DuplikatGrupperingsid>()
        }
    }

    @Test
    fun `oppgave med eksternId som finnes fra for - returnerer DuplikatEksternIdOgMerkelapp`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val kafkaProducer = FakeHendelseProdusent()
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = kafkaProducer,
            produsentRepository = produsentRepository,
        ) {
            val eksisterendeOppgave = HendelseModel.OppgaveOpprettet(
                hendelseId = uuid("aa"),
                notifikasjonId = uuid("aa"),
                virksomhetsnummer = "1",
                produsentId = "someproducer",
                kildeAppNavn = "test",
                merkelapp = "tag",
                eksternId = "oppgave-ekstern-id",
                mottakere = listOf(HendelseModel.AltinnRessursMottaker("1", "test-fager")),
                tekst = "en tekst",
                grupperingsid = "gr-1",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                eksterneVarsler = emptyList(),
                påminnelse = null,
                hardDelete = null,
                frist = null,
                sakId = null,
            ).also { produsentRepository.oppdaterModellEtterHendelse(it) }

            val resultat = client.nySakOgOppgave(oppgaveEksternId = eksisterendeOppgave.eksternId)
                .nySakOgOppgaveResultat<Error.DuplikatEksternIdOgMerkelapp>()
            assertEquals(eksisterendeOppgave.notifikasjonId, resultat.idTilEksisterende)
        }
    }

    @Test
    fun `sak har vært hard-deleted - returnerer DuplikatGrupperingsidEtterDelete`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentRepository = ProdusentRepositoryImpl(database)
        ktorProdusentTestServer(
            kafkaProducer = FakeHendelseProdusent(),
            produsentRepository = produsentRepository,
        ) {
            val sakId = uuid("bb")
            val sakOpprettet = HendelseModel.SakOpprettet(
                hendelseId = sakId,
                sakId = sakId,
                virksomhetsnummer = "1",
                produsentId = "someproducer",
                kildeAppNavn = "test",
                merkelapp = "tag",
                grupperingsid = "gr-1",
                mottakere = listOf(HendelseModel.AltinnRessursMottaker("1", "test-fager")),
                tittel = "en tittel",
                tilleggsinformasjon = null,
                lenke = null,
                oppgittTidspunkt = null,
                mottattTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                nesteSteg = null,
                hardDelete = null,
            ).also { produsentRepository.oppdaterModellEtterHendelse(it) }

            val hardDelete = HendelseModel.HardDelete(
                hendelseId = uuid("cc"),
                aggregateId = sakId,
                virksomhetsnummer = sakOpprettet.virksomhetsnummer,
                produsentId = sakOpprettet.produsentId,
                kildeAppNavn = sakOpprettet.kildeAppNavn,
                deletedAt = OffsetDateTime.parse("2020-06-01T01:01Z"),
                grupperingsid = sakOpprettet.grupperingsid,
                merkelapp = sakOpprettet.merkelapp,
            ).also { produsentRepository.oppdaterModellEtterHendelse(it) }

            client.nySakOgOppgave()
                .nySakOgOppgaveResultat<Error.DuplikatGrupperingsidEtterDelete>()
        }
    }


private suspend inline fun <reified T : MutationNySakOgOppgave.NySakOgOppgaveResultat> HttpResponse.nySakOgOppgaveResultat(): T {
    val resultat = getTypedContent<MutationNySakOgOppgave.NySakOgOppgaveResultat>("nySakOgOppgave")
    return resultat as T
}

private suspend fun HttpClient.nySakOgOppgave(
    grupperingsid: String = "gr-1",
    sakTittel: String = "en tittel",
    oppgaveEksternId: String = "oppgave-ekstern-id",
    oppgaveTekst: String = "oppgave tekst",
) = produsentApi(
    """
    mutation {
        nySakOgOppgave(
            virksomhetsnummer: "1"
            merkelapp: "tag"
            grupperingsid: "$grupperingsid"
            mottakere: [{ altinnRessurs: { ressursId: "test-fager" } }]
            sak: {
                tittel: "$sakTittel"
                tilleggsinformasjon: "her er noe tilleggsinformasjon"
                initiellStatus: MOTTATT
            }
            oppgave: {
                eksternId: "$oppgaveEksternId"
                tekst: "$oppgaveTekst"
                lenke: "https://nav.no"
            }
        ) {
            __typename
            ... on NySakOgOppgaveVellykket {
                sakId
                oppgaveId
            }
            ... on DuplikatEksternIdOgMerkelapp {
                feilmelding
                idTilEksisterende
            }
            ... on DuplikatGrupperingsid {
                feilmelding
                idTilEksisterende
            }
            ... on UgyldigMottaker {
                feilmelding
            }
        }
    }
    """
)
