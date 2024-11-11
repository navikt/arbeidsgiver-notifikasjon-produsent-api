package no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase

class MaskingAppender: AppenderBase<ILoggingEvent>() {

    var appender: Appender<ILoggingEvent>? = null

    override fun append(event: ILoggingEvent) {
        appender?.doAppend(
            object : ILoggingEvent by event {
                override fun getFormattedMessage(): String? =
                    mask(event.formattedMessage)

                override fun getThrowableProxy(): IThrowableProxy? {
                    if (event.throwableProxy == null) {
                        return null
                    }
                    return object : IThrowableProxy by event.throwableProxy {
                        override fun getMessage(): String? =
                            mask(event.throwableProxy.message)
                    }
                }

            }
        )
    }

    companion object {
        val FNR = Regex("""(^|\D)\d{11}(?=$|\D)""")
        val ORGNR = Regex("""(^|\D)\d{9}(?=$|\D)""")
        val EPOST = Regex("""(^|\D)[\w.-]+@[\w.-]+(?=$|\D)""")

        fun mask(string: String?): String? {
            return string?.let {
                FNR.replace(it, "$1***********")
                    .replace(ORGNR, "$1*********")
                    .replace(EPOST, "$1********")
            }
        }
    }
}

