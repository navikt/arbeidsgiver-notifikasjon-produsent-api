package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.annotation.JsonValue
import graphql.schema.DataFetchingEnvironment
import java.util.*

@JsonTypeName("PageInfo")
data class PageInfo(
    val endCursor: Cursor,
    val hasNextPage: Boolean
)

@JsonTypeName("Edge")
data class Edge<T>(
    val cursor: Cursor,
    val node: T
)

data class Cursor(
    @get:JsonValue val value: String
) {
    operator fun compareTo(other: Cursor): Int = offset.compareTo(other.offset)
    operator fun plus(increment: Int): Cursor = of(offset + increment)
    override fun toString(): String = value

    val next: Cursor
        get() = this + 1

    val offset: Int
        get() = Integer.parseInt(
            String(
                Base64.getDecoder().decode(value)
            ).replace(PREFIX, "")
        )

    companion object {
        const val PREFIX = "cur"

        fun of(offset: Int): Cursor {
            return Cursor(
                Base64.getEncoder().encodeToString(
                    "$PREFIX$offset".toByteArray()
                )
            )
        }

        fun empty(): Cursor {
            return of(0)
        }
    }
}

interface Connection<T> {
    companion object {
        fun <T, I : Connection<T>> create(
            data: List<T>,
            env: DataFetchingEnvironment,
            factory: (edges: List<Edge<T>>, pageInfo: PageInfo) -> I
        ): I {
            if (data.isEmpty()) {
                return factory(emptyList(), PageInfo(Cursor.empty(), false))
            }

            val first = env.getArgumentOrDefault("first", data.size)
            val after = Cursor(env.getArgumentOrDefault("after", Cursor.empty().value))
            val cursors = generateSequence(after.next) { it.next }.iterator()
            val edges = data.map {
                Edge(cursors.next(), it)
            }.subList(0, first)
            val pageInfo = PageInfo(
                edges.last().cursor,
                edges.last().cursor >= after.plus(first)
            )

            return factory(edges, pageInfo)
        }
    }
}
