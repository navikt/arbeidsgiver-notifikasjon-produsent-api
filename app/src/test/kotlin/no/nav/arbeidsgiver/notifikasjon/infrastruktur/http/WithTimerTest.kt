package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WithTimerTest {

    @Test
    fun `scrape fungerer etter at withTimer har feilet`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            withTimer("with_timer_test", Tags.of("provider", "test")) {
                throw IllegalStateException("feil")
            }
        }

        val scrape = Metrics.meterRegistry.scrape()
        assertTrue(scrape.contains("""throwable="java.lang.IllegalStateException""""), scrape)
        assertTrue(scrape.contains("""http_client_seconds_bucket{name="with_timer_test""""), scrape)
    }
}
