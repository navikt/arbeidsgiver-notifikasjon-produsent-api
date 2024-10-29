package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.writeValueAsStringSupportingTypeInfoInCollections
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.NonBlockingDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.intellij.lang.annotations.Language
import java.io.Closeable
import java.sql.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

/** Encapsulate a DataSource, and expose it through an higher-level interface which
 * takes care of of cleaning up all resources, and where it's clear whether you
 * are running a single command or a transaction.
 */
class Database private constructor(
    private val config: Config,
    private val dataSource: NonBlockingDataSource<*>,
): Closeable {
    data class Config(
        val host: String,
        val port: String,
        val database: String,
        val username: String,
        val password: String,
        val migrationLocations: String,
        val jdbcOpts: Map<String, Any> = mapOf()
    ) {
        val jdbcUrl: String
            get() = "jdbc:postgresql://$host:$port/$database?${jdbcOpts.entries.joinToString("&")}"
    }

    override fun close() {
        dataSource.close()
    }

    companion object {
        private val log = logger()

        fun config(name: String, envPrefix: String = "DB", jdbcOpts: Map<String, Any> = mapOf()) = Config(
            host = System.getenv("${envPrefix}_HOST") ?: "localhost",
            port = System.getenv("${envPrefix}_PORT") ?: "1337",
            username = System.getenv("${envPrefix}_USERNAME") ?: "postgres",
            password = System.getenv("${envPrefix}_PASSWORD") ?: "postgres",
            database = System.getenv("${envPrefix}_DATABASE") ?: name.replace('_', '-'),
            migrationLocations = "db/migration/$name",
            jdbcOpts = jdbcOpts,
        )

        private fun Config.asHikariConfig(): HikariConfig {
            val config = this
            return HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                driverClassName = "org.postgresql.Driver"
                metricRegistry = Metrics.meterRegistry
                minimumIdle = 1
                maximumPoolSize = 10
                connectionTimeout = 10000
                idleTimeout = 10001
                maxLifetime = 30001
                leakDetectionThreshold = 30000
            }
        }

        suspend fun openDatabase(
            config: Config,
            flywayAction: Flyway.() -> Unit = { migrate () },
        ): Database {
            val hikariConfig = config.asHikariConfig()

            /* When the application runs in kubernetes and connects to the
             * database throught a cloudsql-proxy sidecar, we might be
             * ready to connect to the the database before the sidecare
             * is ready.
             */
            var tryingFor = Duration.ofSeconds(0)
            val sleepFor = Duration.ofSeconds(1)
            while (true) {
                val e = hikariConfig.connectionPossible() ?: break
                if (tryingFor >= Duration.ofMinutes(1)) {
                    throw e
                }
                delay(sleepFor.toMillis())
                tryingFor += sleepFor
            }

            val dataSource = NonBlockingDataSource(HikariDataSource(hikariConfig))
            dataSource.withFlyway(config.migrationLocations) {
                flywayAction()
            }

            return Database(config, dataSource)
        }

        private suspend fun HikariConfig.connectionPossible(): Exception? {
            log.info("attempting database connection")
            return try {
                withContext(Dispatchers.IO) {
                    DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute("select 1")
                        }
                    }
                }
                log.info("attempting database connection: success")
                null
            } catch (e: Exception) {
                log.info("attempting database connection: fail with exception", e)
                e
            }
        }

        fun CoroutineScope.openDatabaseAsync(config: Config): Deferred<Database> {
            return async {
                try {
                    openDatabase(config).also {
                        Health.subsystemReady[Subsystem.DATABASE] = true
                    }
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }
        }
    }

    suspend fun <T> nonTransactionalExecuteQuery(
        @Language("PostgreSQL") sql: String,
        setup: ParameterSetters.() -> Unit = {},
        transform: ResultSet.() -> T,
    ): List<T> =
        dataSource.withConnection { connection ->
            Transaction(connection).executeQuery(sql, setup, transform)
        }

    suspend fun nonTransactionalExecuteUpdate(
        @Language("PostgreSQL") sql: String,
        setup: ParameterSetters.() -> Unit = {},
    ): Int = dataSource.withConnection { connection ->
        Transaction(connection).executeUpdate(sql, setup)
    }

    suspend fun <T> nonTransactionalExecuteBatch(
        @Language("PostgreSQL") sql: String,
        iterable: Iterable<T>,
        setup: ParameterSetters.(it: T) -> Unit = {},
    ): IntArray = dataSource.withConnection { connection ->
        Transaction(connection).executeBatch(sql, iterable, setup)
    }

    suspend fun <T> transaction(
        rollback: (e: Exception) -> T = { throw it },
        body: Transaction.() -> T,
    ): T =
        dataSource.withConnection { connection ->
            val savedAutoCommit = connection.autoCommit
            connection.autoCommit = false

            try {
                val result = Transaction(connection).body()
                connection.commit()
                result
            } catch (e: Exception) {
                connection.rollback()
                rollback(e)
            } finally {
                connection.autoCommit = savedAutoCommit
            }
        }

    suspend fun withFlyway(flywayConfig: FluentConfiguration.() -> Unit, body: Flyway.() -> Unit) {
        dataSource.withFlyway(config.migrationLocations, flywayConfig, body)
    }
}


@JvmInline
value class Transaction(
    private val connection: Connection,
) {
    fun <T> executeQuery(
        @Language("PostgreSQL") sql: String,
        setup: ParameterSetters.() -> Unit = {},
        transform: ResultSet.() -> T,
    ): List<T> {
        return measureSql(sql) {
            connection
                .prepareStatement(sql)
                .use { preparedStatement ->
                    ParameterSetters(preparedStatement).apply(setup)
                    preparedStatement.executeQuery().use { resultSet ->
                        val resultList = mutableListOf<T>()
                        while (resultSet.next()) {
                            resultList.add(resultSet.transform())
                        }
                        resultList
                    }
                }
        }
    }

    fun executeUpdate(
        @Language("PostgreSQL") sql: String,
        setup: ParameterSetters.() -> Unit = {},
    ): Int {
        return measureSql(sql) {
            connection
                .prepareStatement(sql)
                .use { preparedStatement ->
                    ParameterSetters(preparedStatement).apply(setup)
                    preparedStatement.executeUpdate()
                }
        }
    }

    fun <T> executeBatch(
        @Language("PostgreSQL") sql: String,
        iterable: Iterable<T>,
        setup: ParameterSetters.(it: T) -> Unit = {},
    ): IntArray {
        if (iterable.none()) {
            return intArrayOf()
        }
        return measureSql(sql) {
            connection
                .prepareStatement(sql)
                .use { preparedStatement ->
                    iterable.forEach {
                        ParameterSetters(preparedStatement).setup(it)
                        preparedStatement.addBatch()
                    }
                    preparedStatement.executeBatch()
                }
        }
    }

}

class ParameterSetters(
    private val preparedStatement: PreparedStatement,
) {
    private var index = 1


    fun <T: Enum<T>>enumAsText(value: T) = text(value.toString())

    fun text(value: String) = preparedStatement.setString(index++, value)
    fun nullableText(value: String?) = preparedStatement.setString(index++, value)
    fun integer(value: Int) = preparedStatement.setInt(index++, value)
    fun long(value: Long) = preparedStatement.setLong(index++, value)
    fun boolean(newState: Boolean) = preparedStatement.setBoolean(index++, newState)
    fun nullableBoolean(value: Boolean?) = if (value == null) preparedStatement.setNull(index++, Types.BOOLEAN) else boolean(value)
    fun uuid(value: UUID) = preparedStatement.setObject(index++, value)
    fun nullableUuid(value: UUID?) = preparedStatement.setObject(index++, value)
    /**
     * all timestamp values must be `truncatedTo` micros to avoid rounding/precision errors when writing and reading
     **/
    fun nullableTimestamptz(value: OffsetDateTime?) = preparedStatement.setObject(index++, value?.truncatedTo(ChronoUnit.MICROS))
    fun timestamp_without_timezone_utc(value: OffsetDateTime) = timestamp_without_timezone(value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())
    fun timestamp_without_timezone_utc(value: Instant) = timestamp_without_timezone(LocalDateTime.ofInstant(value, ZoneOffset.UTC))
    fun timestamp_without_timezone(value: LocalDateTime) = preparedStatement.setObject(index++, value.truncatedTo(ChronoUnit.MICROS))
    fun timestamp_with_timezone(value: OffsetDateTime) = preparedStatement.setObject(index++, value.truncatedTo(ChronoUnit.MICROS))
    fun bytea(value: ByteArray) = preparedStatement.setBytes(index++, value)
    fun byteaOrNull(value: ByteArray?) = preparedStatement.setBytes(index++, value)
    fun toInstantAsText(value: OffsetDateTime) = instantAsText(value.toInstant())
    fun instantAsText(value: Instant) = text(value.toString())
    fun offsetDateTimeAsText(value: OffsetDateTime) = text(value.toString())
    fun nullableInstantAsText(value: Instant?) = nullableText(value?.toString())

    fun nullableLocalDateTimeAsText(value: LocalDateTime?) = nullableText(value?.toString())
    fun localDateTimeAsText(value: LocalDateTime) = text(value.toString())
    fun periodAsText(value: ISO8601Period) = text(value.toString())

    fun nullableDate(value: LocalDate?) =
        preparedStatement.setDate(index++, value?.let { java.sql.Date.valueOf(it) } )
    fun date(value: LocalDate) =
        preparedStatement.setDate(index++, value.let { java.sql.Date.valueOf(it) })


    inline fun <reified T> nullableJsonb(value: T?) =
        nullableText(
            value?.let { laxObjectMapper.writeValueAsStringSupportingTypeInfoInCollections(value) }
        )
    inline fun <reified T> jsonb(value: T) =
        text(
            laxObjectMapper.writeValueAsStringSupportingTypeInfoInCollections(value)
        )

    fun nullableEnumAsTextList(value: List<Enum<*>>?) {
        nullableTextArray(value?.map { it.toString() })
    }

    fun textArray(value: List<String>) {
        val array = preparedStatement.connection.createArrayOf(
            "text",
            value.toTypedArray()
        )
        preparedStatement.setArray(index++, array)
    }

    fun nullableTextArray(value: List<String>?) {
        if (value == null) {
            preparedStatement.setArray(index++, value)
        } else {
            textArray(value)
        }
    }

    fun uuidArray(value: Collection<UUID>) {
        val array = preparedStatement.connection.createArrayOf(
            "uuid",
            value.toTypedArray()
        )
        preparedStatement.setArray(index++, array)
    }
}


fun ResultSet.getUuid(column: String): UUID =
    getObject(column, UUID::class.java)

fun <T> measureSql(sql: String, action: () -> T): T {
    val timer = getTimer(
        name = "database.execution",
        tags = setOf("sql" to sql),
        description = "Execution time for sql query or update"
    )
    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    return timer.record(action)
}
