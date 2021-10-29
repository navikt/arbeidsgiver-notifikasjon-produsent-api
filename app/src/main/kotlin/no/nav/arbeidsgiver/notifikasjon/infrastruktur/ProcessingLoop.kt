package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration

fun CoroutineScope.launchProcessingLoop(
    debugDescription: String,
    pauseAfterEach: Duration? = null,
    init: suspend () -> Unit = {},
    action: suspend () -> Unit
) {
    val log = LoggerFactory.getLogger("no.nav.processing_loop.$debugDescription")!!

    this.launch {

        init()

        var errors = 0

        while (true) {
            try {
                action()
                errors = 0
            } catch (e: Exception) {
                errors += 1
                log.error("exception in processing loop for $debugDescription", e)
            }

            when {
                errors == 0 && pauseAfterEach != null -> {
                    delay(pauseAfterEach.toMillis())
                }
                errors > 0 -> {
                    val backoff = (pauseAfterEach ?: Duration.ofSeconds(1))
                        .multipliedBy(2.toThePowerOf(errors))
                        .coerceAtMost(Duration.ofHours(1))
                    log.info("backoff $debugDescription: $backoff")
                    delay(backoff.toMillis())
                }
            }
        }
    }
}
