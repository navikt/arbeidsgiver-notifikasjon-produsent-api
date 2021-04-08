package no.nav.arbeidsgiver.notifikasjon

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

val hikariConfig = HikariConfig().apply {
    jdbcUrl = String.format(
        "jdbc:postgresql://%s:%s/%s",
        System.getenv("DB_HOST") ?: "localhost",
        System.getenv("DB_PORT") ?: "5432",
        System.getenv("DB_DATABASE") ?: "postgres"
    )
    username =  System.getenv("DB_USERNAME") ?: "postgres"
    password = System.getenv("DB_PASSWORD") ?: "postgres"
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