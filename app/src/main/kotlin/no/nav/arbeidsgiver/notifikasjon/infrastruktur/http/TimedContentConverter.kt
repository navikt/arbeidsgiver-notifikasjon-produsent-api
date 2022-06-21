package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.coRecord


class TimedContentConverter(private val base: ContentConverter) : ContentConverter {
    private val receiveTimer = Metrics.meterRegistry.timer("content.converter.receive")
    private val sendTimer = Metrics.meterRegistry.timer("content.converter.send")

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        return receiveTimer.coRecord {
            base.convertForReceive(context)
        }
    }

    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any,
    ): Any? {
        return sendTimer.coRecord {
            base.convertForSend(context, contentType, value)
        }
    }

}