package no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database

import com.zaxxer.hikari.HikariDataSource
import org.intellij.lang.annotations.Language
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/** Database non-persisted, local database. Each instantiation of `EphemeralDatabase`
 * creates a fresh database, independent of all other `EphemeralDatabase`-instances.
 *
 * Remember to close the database to free up resources, if instances are created and
 * discarded besides startup and shutdown. * */
class EphemeralDatabase(
    name: String
): AutoCloseable {
    /** Creates a temporary file, so we can have multiple connections to
     * the same database. */
    private val dbFile = File.createTempFile("${name}_", ".sqlite")
    private val database = HikariDataSource().apply {
        jdbcUrl = "jdbc:sqlite:$dbFile"
    }

    override fun close() {
        database.close()
        dbFile.delete()
    }

    fun useConnection(block: Connection.() -> Unit) {
        database.connection.use(block)
    }

    fun useTransaction(block: Connection.() -> Unit) {
        database.connection.use {
            it.autoCommit = false
            try {
                block(it)
                it.commit()
            } catch (e: Exception) {
                it.rollback()
                throw e
            } finally {
                it.autoCommit = true
            }
        }
    }

}

fun <T> Connection.usePrepareStatement(
    @Language("sqlite") sql: String,
    block: PreparedStatement.() -> T
): T {
    return this.prepareStatement(sql).use {
        block(it)
    }
}

fun <T> PreparedStatement.useExecuteQuery(block: ResultSet.() -> T): T {
    return executeQuery().use {
        block(it)
    }
}
