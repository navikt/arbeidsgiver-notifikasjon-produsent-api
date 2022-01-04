package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.Statistikk
import no.nav.arbeidsgiver.notifikasjon.statistikk.StatistikkModel
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant.now

class KafkaReaperModelTest : DescribeSpec({
    val database = testDatabase(Statistikk.databaseConfig)
    val model = StatistikkModel(database)

    describe("Idempotent oppførsel") {
        forAll<Hendelse>(EksempelHendelse.Alle) { hendelse ->
            val metadata = HendelseMetadata(now())
            it("håndterer ${hendelse::class.simpleName} med idempotens") {
                model.oppdaterModellEtterHendelse(hendelse, metadata)
                model.oppdaterModellEtterHendelse(hendelse, metadata)
            }
        }
    }
})