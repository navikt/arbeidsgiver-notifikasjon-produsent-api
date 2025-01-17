package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class DatabaseConfigTest : DescribeSpec({

    describe("JdbcUrl") {
        it("from jdbcUrl yields correct database, username, password and url") {
            val url = "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword"
            val jdbcUrl = JdbcUrl(url)

            jdbcUrl.database shouldBe "mydb"
            jdbcUrl.username shouldBe "myuser"
            jdbcUrl.password shouldBe "mypassword"
        }

        it("supports additional options via map") {
            val url = "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword"
            val jdbcUrl = JdbcUrl(url, mapOf("foo" to "bar")).toString()

            jdbcUrl shouldBe "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword&foo=bar"
        }

        it("can create a copy with another database name") {
            val url = "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword"
            val jdbcUrl = JdbcUrl(url)

            jdbcUrl.withDatabase("anotherdb").toString() shouldBe "jdbc:postgresql://localhost:5432/anotherdb?user=myuser&password=mypassword"

        }
    }

    describe("Database.Config") {
        it("from jdbcUrl yields correct database, username, password and url") {
            val jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword"
            val config = Database.Config(
                jdbcUrl = jdbcUrl,
                migrationLocations = "",
                jdbcOpts = mapOf()
            )

            config.database shouldBe "mydb"
            config.username shouldBe "myuser"
            config.password shouldBe "mypassword"
            config.url.toString() shouldBe jdbcUrl
        }

        it("supports additional options via map") {
            val jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword"
            val config = Database.Config(
                jdbcUrl = jdbcUrl,
                migrationLocations = "",
                jdbcOpts = mapOf("foo" to "bar")
            )

            config.url.toString() shouldBe "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword&foo=bar"
        }

        it("can create a copy with another database name") {
            val jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword"
            val config = Database.Config(
                jdbcUrl = jdbcUrl,
                migrationLocations = "",
                jdbcOpts = mapOf()
            )

            config.withDatabase("anotherdb").url.toString() shouldBe "jdbc:postgresql://localhost:5432/anotherdb?user=myuser&password=mypassword"
        }
    }
})
