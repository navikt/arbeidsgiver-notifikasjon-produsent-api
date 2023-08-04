package no.nav.arbeidsgiver.notifikasjon.hendelse

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import java.util.*

open class HardDeletedRepository(private val database: Database) {
    suspend fun erHardDeleted(aggregateId: UUID) =
        database.nonTransactionalExecuteQuery("""
            select * from hard_deleted_aggregates where aggregate_id = ?
            """,
            { uuid(aggregateId) }
        ) {}.isNotEmpty()

    fun registrerHardDelete(tx: Transaction, hendelse: HendelseModel.Hendelse) {
        if (hendelse !is HendelseModel.HardDelete) {
            return
        }

        tx.executeUpdate("""
            insert into hard_deleted_aggregates(aggregate_id) values (?)
            on conflict do nothing
        """) {
            uuid(hendelse.aggregateId)
        }
    }
}