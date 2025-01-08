package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime

class SkedulertHardDeleteIdempotensTests : DescribeSpec({

    describe("SkedulertHardDelete Idempotent oppførsel") {
        val database = testDatabase(SkedulertHardDelete.databaseConfig)
        val repository = SkedulertHardDeleteRepositoryImpl(database)
        withData(EksempelHendelse.Alle) { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
            repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
        }
    }

    describe("Replay kalenderavtale oppdatert på slettet aggregat er idempotent") {
        val database = testDatabase(SkedulertHardDelete.databaseConfig)
        val repository = SkedulertHardDeleteRepositoryImpl(database)
        val opprettet = EksempelHendelse.KalenderavtaleOpprettet.copy(
            hendelseId = uuid("1"),
            notifikasjonId = uuid("1"),
            virksomhetsnummer = "896929119",
            merkelapp = "Dialogmøte",
            grupperingsid = "9a20ed31-f569-40a0-aeab-730919389278",
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2025-01-06T13:55:19"))
        )
        val oppdatert = EksempelHendelse.KalenderavtaleOppdatert.copy(
            hendelseId = uuid("2"),
            notifikasjonId = opprettet.notifikasjonId,
            virksomhetsnummer = opprettet.virksomhetsnummer,
            merkelapp = opprettet.merkelapp,
            grupperingsid = opprettet.grupperingsid,
            hardDelete = HendelseModel.HardDeleteUpdate(
                nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2025-01-06T12:59:05")),
                strategi = HendelseModel.NyTidStrategi.OVERSKRIV
            )
        )
        val hardDelete = EksempelHendelse.HardDelete.copy(
            hendelseId = uuid("3"),
            aggregateId = opprettet.notifikasjonId,
            virksomhetsnummer = opprettet.virksomhetsnummer,
            merkelapp = opprettet.merkelapp,
            grupperingsid = null, // delete av kalenderavtale via skedulert harddelete
            deletedAt = OffsetDateTime.parse("2025-01-06T12:03:13.081580582Z"),
        )

        repository.oppdaterModellEtterHendelse(opprettet, Instant.EPOCH)
        repository.oppdaterModellEtterHendelse(oppdatert, Instant.EPOCH)
        repository.oppdaterModellEtterHendelse(hardDelete, Instant.EPOCH)

        // full replay
        repository.oppdaterModellEtterHendelse(opprettet, Instant.EPOCH)
        repository.oppdaterModellEtterHendelse(oppdatert, Instant.EPOCH)
        repository.oppdaterModellEtterHendelse(hardDelete, Instant.EPOCH)

        // partial replay
        repository.oppdaterModellEtterHendelse(oppdatert, Instant.EPOCH)
        repository.oppdaterModellEtterHendelse(hardDelete, Instant.EPOCH)

        // partial replay
        repository.oppdaterModellEtterHendelse(hardDelete, Instant.EPOCH)
    }
})
