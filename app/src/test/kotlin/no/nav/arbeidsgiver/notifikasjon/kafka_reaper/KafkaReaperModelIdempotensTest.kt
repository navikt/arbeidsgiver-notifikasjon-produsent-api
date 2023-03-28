package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class KafkaReaperModelIdempotensTest : DescribeSpec({
    val database = testDatabase(KafkaReaper.databaseConfig)
    val model = KafkaReaperModelImpl(database)

    describe("Kafka Reaper Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            model.oppdaterModellEtterHendelse(hendelse)
            model.oppdaterModellEtterHendelse(hendelse)
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                model.oppdaterModellEtterHendelse(EksempelHendelse.HardDelete.copy(
                    virksomhetsnummer = hendelse.virksomhetsnummer,
                    aggregateId = hendelse.aggregateId,
                ))
                model.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})