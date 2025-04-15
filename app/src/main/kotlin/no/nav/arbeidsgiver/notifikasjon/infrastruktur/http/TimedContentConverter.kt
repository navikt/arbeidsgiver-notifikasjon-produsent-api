package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.coRecord


class TimedContentConverter(private val base: ContentConverter) : ContentConverter {
    private val deserializeTimer = Metrics.meterRegistry.timer("content.converter.receive")
    private val serializeTimer = Metrics.meterRegistry.timer("content.converter.send")

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel) =
        deserializeTimer.coRecord {
            base.deserialize(charset, typeInfo, content)
        }

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ) = serializeTimer.coRecord {
        base.serialize(contentType, charset, typeInfo, value)
    }

}