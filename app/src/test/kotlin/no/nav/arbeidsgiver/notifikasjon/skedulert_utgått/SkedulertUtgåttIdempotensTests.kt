package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.NoopHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant

class SkedulertUtgåttIdempotensTests : DescribeSpec({
    val service = SkedulertUtgåttService(NoopHendelseProdusent)

    describe("AutoSlett Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            service.processHendelse(hendelse)
            service.processHendelse(hendelse)
        }
    }
})
