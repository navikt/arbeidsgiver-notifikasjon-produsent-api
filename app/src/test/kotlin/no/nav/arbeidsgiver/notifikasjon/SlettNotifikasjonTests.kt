package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration


@ExperimentalTime
class SlettNotifikasjonTests : DescribeSpec({

    val embeddedKafka = EmbeddedKafkaTestListener()
    listener(embeddedKafka)
    val producer = embeddedKafka.newProducer()
    val consumer = embeddedKafka.newConsumer()

    describe("send tombstone") {
        val key = UUID.randomUUID()
        val hendelse = Hendelse.BrukerKlikket(
            virksomhetsnummer = "1",
            fnr="2",
            notifikasjonsId = UUID.randomUUID()
        )
        producer.brukerKlikket(hendelse)
        producer.slett(Hendelse.SlettHendelse(key, hendelse.virksomhetsnummer))

        it("sends tombstone record to kafka") {
            val poll = consumer.poll(seconds(5).toJavaDuration())
            // how many?
            val value = poll.last().value()
            value shouldBe null
        }
    }
})

