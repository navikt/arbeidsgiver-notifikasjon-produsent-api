package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

class Altinn3VarselKlientMedCacheTest {

    private class FakeTicker : Ticker {
        val nanos = AtomicLong(0)
        override fun read() = nanos.get()
        fun advance(duration: Duration) = nanos.addAndGet(duration.toNanos())
    }

    private class TellendeKlient : Altinn3VarselKlient {
        val shipmentKall = mutableMapOf<String, Int>()
        val orderKall = AtomicLong(0)

        override suspend fun shipment(shipmentId: String): Altinn3VarselKlient.ShipmentResponse {
            shipmentKall.merge(shipmentId, 1, Int::plus)
            return Altinn3VarselKlientLogging().shipment(shipmentId)
        }

        override suspend fun order(
            eksternVarsel: EksternVarsel,
            idempotencyId: String
        ): Altinn3VarselKlient.OrderResponse {
            orderKall.incrementAndGet()
            return Altinn3VarselKlientLogging().order(eksternVarsel, idempotencyId)
        }
    }

    @Test
    fun `shipment hentes maks en gang per ttl per shipmentId`() = runTest {
        val ticker = FakeTicker()
        val delegate = TellendeKlient()
        val klient = Altinn3VarselKlientMedCache(delegate, ttl = Duration.ofMinutes(1), ticker = ticker)

        repeat(5) { klient.shipment("a") }
        klient.shipment("b")
        assertEquals(mapOf("a" to 1, "b" to 1), delegate.shipmentKall)

        ticker.advance(Duration.ofSeconds(59))
        klient.shipment("a")
        assertEquals(1, delegate.shipmentKall["a"])

        ticker.advance(Duration.ofSeconds(2))
        klient.shipment("a")
        assertEquals(2, delegate.shipmentKall["a"])
    }

    @Test
    fun `order caches ikke`() = runTest {
        val delegate = TellendeKlient()
        val klient = Altinn3VarselKlientMedCache(delegate, ticker = FakeTicker())
        val varsel = EksternVarsel.Sms(
            fnrEllerOrgnr = "1",
            sendeVindu = EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
            mobilnummer = "99999999",
            tekst = "hei",
            ordreId = null,
        )

        klient.order(varsel, "x")
        klient.order(varsel, "x")
        assertEquals(2, delegate.orderKall.get())
    }
}
