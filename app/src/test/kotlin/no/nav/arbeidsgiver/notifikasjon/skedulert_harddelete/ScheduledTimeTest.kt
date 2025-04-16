package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduledTimeTest {
    @Test
    fun `ScheduledTimeTest#happensAt`() {

        // spec med absolutt tidspunkt
        with(ScheduledTime.parse(spec = "2022-04-01T12:30", base = "2022-04-01T12:30:00.00Z")) {
            assertEquals(Instant.parse("2022-04-01T10:30:00.00Z"), happensAt())
        }

        // spec med timer-duration
        with(ScheduledTime.parse(spec = "PT5H", base = "2022-04-01T12:30:00.00Z")) {
            assertEquals(Instant.parse("2022-04-01T17:30:00.00Z"), happensAt())
        }

        // spec med år-duration
        with(ScheduledTime.parse(spec = "P1YT5H", base = "2022-04-01T12:30:00.00Z")) {
            assertEquals(Instant.parse("2023-04-01T17:30:00.00Z"), happensAt())
        }
    }
}


