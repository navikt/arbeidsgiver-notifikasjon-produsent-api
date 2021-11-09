package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime

class Done : Throwable("done")

@OptIn(ExperimentalTime::class)
class EksternVarslingServiceTests : DescribeSpec({
    val log = logger()
    val database = testDatabase(EksternVarsling.databaseConfig)
    val repository = EksternVarslingRepository(database)
    val kafka = embeddedKafka()

    val meldingSendt = AtomicBoolean(false)

    val service = EksternVarslingService(
        eksternVarslingRepository = repository,
        altinnVarselKlient = object: AltinnVarselKlient {
            override suspend fun send(
                eksternVarsel: EksternVarsel
            ): Result<AltinnVarselKlient.AltinnResponse> {
                meldingSendt.set(true)
                return Result.success(AltinnVarselKlient.AltinnResponse.Ok(rå = NullNode.instance))
            }
        },
        kafkaProducer = kafka.newProducer(),
    )

    describe("foobar") {
        repository.oppdaterModellEtterHendelse(Hendelse.OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = uuid("1"),
            hendelseId = uuid("1"),
            produsentId = "",
            kildeAppNavn = "",
            merkelapp = "",
            eksternId = "",
            mottaker = AltinnMottaker(
                virksomhetsnummer = "",
                serviceCode = "",
                serviceEdition = "",
            ),
            tekst = "",
            grupperingsid = "",
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(SmsVarselKontaktinfo(
                varselId = uuid("2"),
                tlfnr = "",
                fnrEllerOrgnr = "",
                smsTekst = "",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
            )),
        ))

        database.nonTransactionalExecuteUpdate("""
            update emergency_break set stop_processing = false where id = 0
        """)

        val serviceJob = service.start(this)

        it("sends message eventually") {
            eventually(kotlin.time.Duration.seconds(5)) {
                meldingSendt.get() shouldBe true
            }
        }

        val consumer = kafka.newConsumer()
        try {
            consumer.forEachEvent { event ->
                log.info("message received $event")
                if (event is Hendelse.EksterntVarselVellykket) {
                    throw Done()
                }
            }
        } catch (e: Done) {
        }

        it("message received from kafka") {
            true shouldBe true
        }

        serviceJob.cancel()
    }
})