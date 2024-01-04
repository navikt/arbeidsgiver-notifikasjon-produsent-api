package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.NoopHendelseProdusent

class SkedulertPåminnelseIdempotensTests : DescribeSpec({
    val service = SkedulertPåminnelseService(NoopHendelseProdusent)

    describe("SkedulertPåminnelse Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            service.processHendelse(hendelse, PartitionHendelseMetadata(0, 0))
            service.processHendelse(hendelse, PartitionHendelseMetadata(0, 0))
        }
    }
})
