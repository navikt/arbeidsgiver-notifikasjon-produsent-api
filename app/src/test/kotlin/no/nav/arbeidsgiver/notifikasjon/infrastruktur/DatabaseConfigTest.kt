package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import kotlin.test.Test
import kotlin.test.assertEquals


class DatabaseConfigTest {

    @Test
    fun JdbcUrl() {
        // from jdbcUrl yields correct database, username, password and url
        with(JdbcUrl("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword")) {
            assertEquals("mydb", database)
            assertEquals("myuser", username)
            assertEquals("mypassword", password)
        }


        // supports additional options via map
        with(
            JdbcUrl(
                "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword",
                mapOf("foo" to "bar")
            ).toString()
        ) {
            assertEquals("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword&foo=bar", this)
        }


        // can create a copy with another database name
        with(JdbcUrl("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword")) {
            assertEquals(
                "jdbc:postgresql://localhost:5432/anotherdb?user=myuser&password=mypassword",
                withDatabase("anotherdb").toString()
            )
        }


    }

    @Test
    fun `Database Config`() {
        // from jdbcUrl yields correct database, username, password and url
        with("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword") {
            val config = Database.Config(
                jdbcUrl = this,
                migrationLocations = "",
                jdbcOpts = mapOf()
            )

            assertEquals("mydb", config.database)
            assertEquals("myuser", config.username)
            assertEquals("mypassword", config.password)
            assertEquals(this, config.url.toString())
        }

        // supports additional options via map
        with("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword") {
            val config = Database.Config(
                jdbcUrl = this,
                migrationLocations = "",
                jdbcOpts = mapOf("foo" to "bar")
            )

            assertEquals(
                "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword&foo=bar",
                config.url.toString()
            )
        }

        // can create a copy with another database name
        with("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword") {
            val config = Database.Config(
                jdbcUrl = this,
                migrationLocations = "",
                jdbcOpts = mapOf()
            )

            assertEquals(
                "jdbc:postgresql://localhost:5432/anotherdb?user=myuser&password=mypassword",
                config.withDatabase("anotherdb").url.toString()
            )
        }
    }
}
