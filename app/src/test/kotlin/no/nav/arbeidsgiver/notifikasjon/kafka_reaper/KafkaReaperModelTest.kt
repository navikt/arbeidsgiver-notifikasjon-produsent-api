package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.KafkaReaper
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class KafkaReaperModelTest : DescribeSpec({
    val database = testDatabase(KafkaReaper.databaseConfig)
    val model = KafkaReaperModelImpl(database)

    describe("Idempotent oppførsel") {
        forAll<Hendelse>(EksempelHendelse.Alle) { hendelse ->
            it("håndterer ${hendelse::class.simpleName} med idempotens") {
                model.oppdaterModellEtterHendelse(hendelse)
                model.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})