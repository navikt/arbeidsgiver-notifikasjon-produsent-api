package db.migration

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CONSUMER_PROPERTIES
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


object MigrationOps {
    fun resetOffsetsToEarliest() {
        OS.exec(
            "kafka-consumer-groups.sh " +
                    "   --bootstrap-server ${CONSUMER_PROPERTIES[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]} " +
                    "   --command-config ${OS.kafkaPropertiesFile} " +
                    "   --group ${CONSUMER_PROPERTIES[ConsumerConfig.GROUP_ID_CONFIG]} " +
                    "   --topic fager.notifikasjon " +
                    "   --reset-offsets --to-earliest --execute"
        )
    }
}

object OS {
    private val log = logger()

    private var cwd : String by Delegates.observable("") {
            _, old, new ->
        log.warn("cwd changed: $old -> $new")
    }

    var kafkaPropertiesFile : String by Delegates.observable("/tmp/kafka.properties") {
            _, old, new ->
        log.warn("kafkaPropertiesFile changed: $old -> $new")
    }

    fun exec(cmd: String) {
        log.info("exec: $cwd$cmd")
        val proc = Runtime.getRuntime().exec("$cwd$cmd")
        proc.waitFor(10, TimeUnit.SECONDS)
        log.info(String(proc.inputStream.readAllBytes()))
        if (proc.exitValue() != 0) {
            throw Error("process exited with error: ${String(proc.errorStream.readAllBytes())}")
        }
    }

    fun setupLocal() {
        cwd = ".kafka-cli/bin/"
        kafkaPropertiesFile = ".kafka-cli/kafka.properties"
    }
}
