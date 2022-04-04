package no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase

class MaskingAppender: AppenderBase<ILoggingEvent>() {

    var appender: Appender<ILoggingEvent>? = null

    override fun append(event: ILoggingEvent) {
        appender?.doAppend(
            object : ILoggingEvent by event {
                override fun getFormattedMessage(): String? =
                    mask(event.formattedMessage)
            }
        )
    }

    companion object {
        private val FNR = Regex("""(^|\D)\d{11}(?=$|\D)""")
        fun mask(string: String?): String? {
            return string?.replace(FNR, "$1***********")
        }
    }
}

