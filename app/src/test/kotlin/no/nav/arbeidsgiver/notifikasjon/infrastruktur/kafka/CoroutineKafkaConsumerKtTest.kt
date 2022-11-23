package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord

class CoroutineKafkaConsumerKtTest : DescribeSpec({

    describe("ConsumerRecord#toLoggableString") {
        withData(
            listOf(
                *EksempelHendelse.Alle.toTypedArray(),
                null
            )
        ) { value ->
            val loggableString = ConsumerRecord("foo.topic", 0, 0, "somekey", value).loggableToString()
            loggableString shouldNot beEmpty()
        }
    }
})
