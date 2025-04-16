package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.kafkaObjectMapper
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ScalarsTest {
    @Test
    fun ISO8601DateTime() {
        mapOf(
            "2020-01-01T00:01Z" to "2020-01-01T00:01:00Z", // 00:01 UTC == 01:01 Oslo
            "2020-01-01T01:01+01:00" to "2020-01-01T00:01:00Z", // 01:01 +1 == 01:01 Oslo
            "2020-01-01T01:01+01:00[Europe/Oslo]" to "2020-01-01T00:01:00Z", // == 01:01 Oslo
            "2020-01-01T01:01" to "2020-01-01T00:01:00Z", // 01:01 Oslo
            "2020-01-01T01:01:00.00" to "2020-01-01T00:01:00Z", // 01:01 Oslo
            "2024-03-31T01:59:00" to "2024-03-31T00:59:00Z", // 01:59 Oslo (rett før sommertid 0200)
            "2024-03-31T02:01:00" to "2024-03-31T01:01:00Z", // 02:01 Oslo (rett etter sommertid, aldri på klokka)
            "2024-03-31T02:59:00" to "2024-03-31T01:59:00Z", // 02:59 Oslo (var aldri på klokka)
            "2024-03-31T03:01:00" to "2024-03-31T01:01:00Z", // 03:01 Oslo (rett etter sommertid 0200)
        ).forEach { (ts, expected) ->
            Scalars.ISO8601DateTime.coercing.parseValue(ts).let {
                it as OffsetDateTime
                assertEquals(OffsetDateTime.parse(expected), it)
                assertEquals("\"$expected\"", kafkaObjectMapper.writeValueAsString(it))
            }
        }
    }
}
