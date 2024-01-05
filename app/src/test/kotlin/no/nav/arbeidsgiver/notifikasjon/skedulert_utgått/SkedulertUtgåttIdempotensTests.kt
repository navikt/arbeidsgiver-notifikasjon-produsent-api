package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.NoopHendelseProdusent

class SkedulertUtgåttIdempotensTests : DescribeSpec({
    val service = SkedulertUtgåttService(NoopHendelseProdusent)

    describe("SkedulertUtgått Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            service.processHendelse(hendelse, PartitionHendelseMetadata(0, 0))
            service.processHendelse(hendelse, PartitionHendelseMetadata(0, 0))
        }
    }
})
