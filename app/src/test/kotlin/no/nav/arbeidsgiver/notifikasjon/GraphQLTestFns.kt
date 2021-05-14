package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.convertValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlin.time.toJavaDuration

data class GraphQLError(
    val message: String,
    val locations: Any?,
    val extensions: Map<String, Any>?
)

fun String.ensurePrefix(prefix: String) =
    prefix + removePrefix(prefix)

inline fun <reified T> TestApplicationResponse.getTypedContent(name: String): T {
    val errors = getGraphqlErrors()
    if (errors.isEmpty()) {
        val tree = objectMapper.readTree(this.content!!)
        val node = tree.get("data").at(name.ensurePrefix("/"))
        return objectMapper.convertValue(node)
    } else {
        throw Exception("Got errors $errors")
    }
}

fun TestApplicationResponse.getGraphqlErrors(): List<GraphQLError> {
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val errors = tree.get("errors")
    return if (errors == null) emptyList() else objectMapper.convertValue(errors)
}


