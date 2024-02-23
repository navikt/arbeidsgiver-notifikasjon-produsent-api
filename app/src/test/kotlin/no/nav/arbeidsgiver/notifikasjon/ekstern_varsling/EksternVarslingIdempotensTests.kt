package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.typeNavn
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class EksternVarslingIdempotensTests : DescribeSpec({

    describe("Ekstern Varlsing Idempotent oppførsel") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        withData(EksempelHendelse.Alle) { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse)
            repository.oppdaterModellEtterHendelse(hendelse)
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                val database = testDatabase(EksternVarsling.databaseConfig)
                val repository = EksternVarslingRepository(database)

                repository.oppdaterModellEtterHendelse(EksempelHendelse.HardDelete.copy(
                    virksomhetsnummer = hendelse.virksomhetsnummer,
                    aggregateId = hendelse.aggregateId,
                ))
                repository.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})
