package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.NoopHendelseProdusent

class SkedulertPåminnelseIdempotensTests : DescribeSpec({
    val (service, _) = setupEngine()

    describe("SkedulertPåminnelse Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            service.processHendelse(hendelse)
            service.processHendelse(hendelse)
        }
    }
})
