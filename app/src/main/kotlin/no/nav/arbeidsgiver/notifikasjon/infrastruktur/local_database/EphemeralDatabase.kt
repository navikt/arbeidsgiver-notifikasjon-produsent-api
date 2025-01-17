package no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.zaxxer.hikari.HikariDataSource
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.writeValueAsStringSupportingTypeInfoInCollections
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.measureSql
import org.intellij.lang.annotations.Language
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/** Non-persisted, local database. Each instantiation of `EphemeralDatabase`
 * creates a fresh database, independent of all other `EphemeralDatabase`-instances.
 *
 * Remember to close the database to free up resources, if instances are created and
 * discarded besides startup and shutdown. * */
class EphemeralDatabase(
    name: String,
    @Language("sqlite") setup: String,
): AutoCloseable {
    /** Creates a temporary file, so we can have multiple connections to
     * the same database. */
    private val dbFile = File.createTempFile("${name}_", ".sqlite")
    private val database = HikariDataSource().apply {
        jdbcUrl = "jdbc:sqlite:$dbFile"
        setAutoCommit(false)
    }

    init {
        useTransaction {
            createStatement().use { statement ->
                statement.executeUpdate(setup)
            }
        }
    }

    override fun close() {
        database.close()
        dbFile.delete()
    }

    fun <T> useTransaction(body: Connection.() -> T): T {
        return database.connection.use { connection ->
            try {
                body(connection).also {
                    connection.commit()
                }
            } catch (e: Exception) {
                connection.rollback()
                throw e
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

fun Connection.executeUpdate(
    @Language("sqlite") sql: String,
    setup: Setup.() -> Unit
): Int = usePrepareStatement(sql) {
    Setup(this).setup()
    measureSql(sql) {
        executeUpdate()
    }
}

fun <T> Connection.executeBatch(
    @Language("sqlite") sql: String,
    batches: Iterable<T>,
    setup: Setup.(it: T) -> Unit,
): IntArray = usePrepareStatement(sql) {
    batches.forEach { batch ->
        Setup(this).setup(batch)
        addBatch()
    }
    measureSql(sql) {
        executeBatch()
    }
}

fun <T> Connection.executeQuery(
    @Language("sqlite") sql: String,
    setup: Setup.() -> Unit,
    result: ResultSet.() -> T,
): T = usePrepareStatement(sql) {
    Setup(this).setup()
    measureSql(sql) {
        executeQuery().use {
            result(it)
        }
    }
}

class Setup(
    private val preparedStatement: PreparedStatement,
) {
    private var index: Int = 1

    fun setText(text: String) = preparedStatement.setString(index++, text)
    fun setTextOrNull(text: String?) = preparedStatement.setString(index++, text)

    fun setUUID(uuid: UUID) = setText(uuid.toString())
    fun setInstant(instant: Instant) = setText(instant.toString())
    fun setLocalDate(localDate: LocalDate) = setText(localDate.toString())
    fun setLocalDateOrNull(localDate: LocalDate?) = setTextOrNull(localDate?.toString())
    fun setLocalDateTimeOrNull(localDateTime: LocalDateTime?) = setTextOrNull(localDateTime?.toString())
    fun <E: Enum<E>> setEnum(enum: E) = setText(enum.name)
    inline fun <reified A>setJson(value: A) =
        setText(ephemeralDatabaseObjectMapper.writeValueAsStringSupportingTypeInfoInCollections<A>(value))
}

fun ResultSet.getUUID(columnLabel: String): UUID = UUID.fromString(getString(columnLabel))
fun ResultSet.getInstant(columnLabel: String): Instant = Instant.parse(getString(columnLabel))
fun ResultSet.getLocalDate(columnLabel: String): LocalDate = LocalDate.parse(getString(columnLabel))
fun ResultSet.getLocalDateOrNull(columnLabel: String) = getString(columnLabel)?.let(LocalDate::parse)
fun ResultSet.getLocalDateTime(columnLabel: String) = LocalDateTime.parse(getString(columnLabel))
fun ResultSet.getLocalDateTimeOrNull(columnLabel: String) = getString(columnLabel)?.let(LocalDateTime::parse)
inline fun <reified E: Enum<E>> ResultSet.getEnum(columnLabel: String): E = enumValueOf(getString(columnLabel))
inline fun <reified A> ResultSet.getJson(columnLabel: String) = ephemeralDatabaseObjectMapper.readValue<A>(getString(columnLabel))

fun <T> ResultSet.resultAsList(extractRow: ResultSet.() -> T): List<T> {
    val result = mutableListOf<T>()
    while (next()) {
        result.add(extractRow())
    }
    return result
}

/* can't be private because it's inlined in `getJson` */
val ephemeralDatabaseObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
}