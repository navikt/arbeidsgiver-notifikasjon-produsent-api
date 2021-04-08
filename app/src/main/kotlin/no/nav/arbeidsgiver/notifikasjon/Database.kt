package no.nav.arbeidsgiver.notifikasjon

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

val hikariConfig = HikariConfig().apply {
    val host = System.getenv("DB_HOST") ?: "localhost"
    val port = System.getenv("DB_PORT") ?: "5432"
    val db = System.getenv("DB_DATABASE") ?: "postgres"

    username =  System.getenv("DB_USERNAME") ?: "postgres"
    password = System.getenv("DB_PASSWORD") ?: "postgres"
    jdbcUrl = "jdbc:postgresql://$host:$port/$db"
    driverClassName = "org.postgresql.Driver"
}

fun hikariDatasource(config: HikariConfig = hikariConfig) = HikariDataSource(config)

object DB {
    val datasource: DataSource
        get() = hikariDatasource()
}

internal fun DataSource.migrate() {
    Flyway.configure()
        .dataSource(this)
        .load()
        .migrate()
}