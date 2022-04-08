package no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import ch.qos.logback.core.spi.ContextAware
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.spi.LifeCycle
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv

@Suppress("unused") /* see resources/META-INF/services/ch.qos.logback.classic.spi */
class LogConfig : ContextAwareBase(), Configurator {
    override fun configure(lc: LoggerContext) {
        val naisCluster = System.getenv("NAIS_CLUSTER_NAME")

        val rootAppender = MaskingAppender().setup(lc) {
            appender = ConsoleAppender<ILoggingEvent>().setup(lc) {
                if (naisCluster != null) {
                    encoder = LogstashEncoder().setup(lc)
                } else {
                    encoder = LayoutWrappingEncoder<ILoggingEvent>().setup(lc).apply {
                        layout = PatternLayout().also {
                            it.pattern = "%d %-5level [%thread] %logger: %msg %mdc%n"
                        }.setup(lc)
                    }
                }
            }
        }

        lc.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            level = basedOnEnv(
                prod = { Level.INFO },
                other = { Level.DEBUG }
            )
            addAppender(rootAppender)
        }

        lc.getLogger("org.apache.kafka").level = Level.INFO
        lc.getLogger("io.netty").level = Level.INFO

        if (naisCluster == null || naisCluster == "dev-gcp") {
            lc.getLogger("io.ktor.auth.jwt").level = Level.TRACE
        }
    }
}

private fun <T> T.setup(context: LoggerContext, body: T.() -> Unit = {}): T
        where T : ContextAware,
              T : LifeCycle
{
    this.context = context
    this.body()
    this.start()
    return this
}