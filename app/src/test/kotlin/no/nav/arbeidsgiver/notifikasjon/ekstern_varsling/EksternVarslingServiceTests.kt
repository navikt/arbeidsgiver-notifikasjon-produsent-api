package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime

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

    runBlocking(Dispatchers.IO) {
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

        launch {
            service.start(this)
        }

        it("sends message eventually") {
            eventually(kotlin.time.Duration.seconds(1)) {
                meldingSendt.get() shouldBe true
            }
        }

        val consumer = kafka.newConsumer()
        consumer.forEachEvent { event ->
            if (event is Hendelse.EksterntVarselVellykket) {
                return@forEachEvent
            }
        }
        it("message received from kafka") {
        }
    }
})