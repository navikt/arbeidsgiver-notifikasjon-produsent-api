package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.typeNavn
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant

class DataproduktIdempotensTests : DescribeSpec({
    val database = testDatabase(Dataprodukt.databaseConfig)
    val subject = DataproduktModel(database)

    val metadata = HendelseMetadata(Instant.now())

    describe("Dataprodukt Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            subject.oppdaterModellEtterHendelse(hendelse, metadata)
            subject.oppdaterModellEtterHendelse(hendelse, metadata)
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                subject.oppdaterModellEtterHendelse(EksempelHendelse.HardDelete.copy(
                    virksomhetsnummer = hendelse.virksomhetsnummer,
                    aggregateId = hendelse.aggregateId,
                ), metadata)
                subject.oppdaterModellEtterHendelse(hendelse, metadata)
            }
        }
    }
})
