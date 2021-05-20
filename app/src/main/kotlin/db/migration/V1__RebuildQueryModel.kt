package db.migration

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.CONSUMER_PROPERTIES
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.concurrent.TimeUnit

@Suppress("unused") // flyway
class V2__RebuildQueryModel : BaseJavaMigration() {
    @Suppress("SqlWithoutWhere")
    override fun migrate(context: Context) {
        context.connection.createStatement().use {
            it.executeUpdate("delete from notifikasjon")
            it.executeUpdate("delete from brukerklikk")
            KafkaOps.resetOffsets()
        }
    }
}

object KafkaOps {
    fun resetOffsets() {
        OS.exec(
            "/tmp/kafka-consumer-groups.sh " +
                    "   --bootstrap-server ${CONSUMER_PROPERTIES[BOOTSTRAP_SERVERS_CONFIG]} " +
                    "   --command-config /kafka.properties " +
                    "   --group ${CONSUMER_PROPERTIES[GROUP_ID_CONFIG]} " +
                    "   --topic arbeidsgiver.notifikasjon " +
                    "   --reset-offsets --to-earliest --execute"
        )
    }
}

object OS {
    val log = logger()

    fun exec(cmd: String) {
        log.info("exec: $cmd")
        val proc = Runtime.getRuntime().exec(cmd)
        proc.waitFor(10, TimeUnit.SECONDS)
        log.info(String(proc.inputStream.readAllBytes()))
        if (proc.exitValue() != 0) {
            throw Error("process exited with error: ${String(proc.errorStream.readAllBytes())}")
        }
    }
}
