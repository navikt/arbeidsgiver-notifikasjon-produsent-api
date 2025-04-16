package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test

class SkedulertUtgåttIdempotensTest {
    @Test
    fun `SkedulertUtgått Idempotent oppførsel`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val repo = SkedulertUtgåttRepository(database)

        EksempelHendelse.Alle.forEach { hendelse ->
            repo.oppdaterModellEtterHendelse(hendelse)
            repo.oppdaterModellEtterHendelse(hendelse)
        }
    }
}
