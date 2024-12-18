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
        ) {}.isNotEmpty() || erCascadeHardDeleted(aggregateId)

    private suspend fun erCascadeHardDeleted(notifikasjonId: UUID) =
        database.nonTransactionalExecuteQuery("""
            select * from hard_delete_sak_til_notifikasjon_kobling
             inner join hard_deleted_aggregates on hard_delete_sak_til_notifikasjon_kobling.sak_id = hard_deleted_aggregates.aggregate_id 
            where hard_delete_sak_til_notifikasjon_kobling.notifikasjon_id = ?
            """,
            { uuid(notifikasjonId) }
        ) {}.isNotEmpty()

    /**
     * her lagres kobling mellom sak og notifikasjon slik at vi kan vite at en notifikasjon er cascade slettet
     */
    suspend fun registrerKoblingForCascadeDelete(hendelse: HendelseModel.AggregatOpprettet) {
        val sakId = hendelse.sakId

        // hopp over hvis sakId er null eller hvis det er opprettelse av sak
        if (sakId != null && sakId != hendelse.aggregateId) {
            database.nonTransactionalExecuteUpdate("""
                insert into hard_delete_sak_til_notifikasjon_kobling(sak_id, notifikasjon_id) values (?, ?)
                on conflict do nothing
            """) {
                uuid(sakId)
                uuid(hendelse.aggregateId)
            }
        }

    }

    fun registrerDelete(tx: Transaction, aggregateId: UUID) {
        tx.executeUpdate("""
            insert into hard_deleted_aggregates(aggregate_id) values (?)
            on conflict do nothing
        """) {
            uuid(aggregateId)
        }
    }
}