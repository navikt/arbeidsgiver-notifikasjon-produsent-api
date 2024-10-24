package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

class PaaminnelseEndresTests : DescribeSpec({

    describe("oppgavePåminnelseEndres-oppførsel") {
        val virksomhetsnummer = "123"
        val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
        val merkelapp = "tag"
        val eksternId = "123"
        val mottaker = AltinnMottaker(
            virksomhetsnummer = virksomhetsnummer,
            serviceCode = "1",
            serviceEdition = "1"
        )

        val oppgaveOpprettetTidspunkt = OffsetDateTime.now()
        val konkretPaaminnelsesTidspunkt = oppgaveOpprettetTidspunkt.toLocalDateTime().plusWeeks(1)

        context("Oppgave har ingen påminnelse men får ny") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = uuid,
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            val req =
                """
                mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid",
                        paaminnelse: {
                            eksterneVarsler:[],
                            tidspunkt: {konkret: "$konkretPaaminnelsesTidspunkt"}
                        }
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """

            val response = engine.produsentApi(
                req
            )

            it("returnerer tilbake oppgave id-en") {
                val vellykket =
                    response.getTypedContent<MutationPaaminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.PaaminnelseEndret>()
                    .last()
                hendelse.påminnelse?.tidspunkt shouldBe HendelseModel.PåminnelseTidspunkt.Konkret(
                    konkretPaaminnelsesTidspunkt,
                    konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                )
            }
        }

        context("Oppgave får fjernet påminnelse") {
            TODO("implementer")
        }
    }
})

private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val kafkaProducer = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentModel
    )
    return Triple(produsentModel, kafkaProducer, engine)
}