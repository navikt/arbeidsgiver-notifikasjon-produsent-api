package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test

class SkedulertPåminnelseIdempotensTest {
    @Test
    fun `SkedulertPåminnelse Idempotent oppførsel`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val service = SkedulertPåminnelseService(
                hendelseProdusent = FakeHendelseProdusent(),
                database = database
            )
            EksempelHendelse.Alle.forEach { hendelse ->
                service.processHendelse(hendelse)
                service.processHendelse(hendelse)
            }
        }
}
