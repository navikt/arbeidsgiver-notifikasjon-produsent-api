package no.nav.arbeidsgiver.notifikasjon.infrastruktur.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.getAsync
import java.util.concurrent.atomic.AtomicInteger

class CaffeineKtTest : DescribeSpec({

    val calls = AtomicInteger(0)

    // works not
    val cache1 = caffeineBuilder<String, String>().build()

    // works
    val cache2 = Caffeine.newBuilder().buildAsync<String, String>()

    fun hello(who: String) : String {
        if (calls.getAndIncrement() == 0) {
            throw Exception("foo")
        }
        return "hello $who"

    }

    suspend fun cachedHello1(who: String) : String {
        return cache1.get(who) {
            hello(who)
        }
    }

    suspend fun cachedHello2(who: String) : String {
        return cache2.getAsync(who) {
            hello(who)
        }
    }

    describe("should only throw once") {
        calls.set(0)
        val who = "me"
        shouldThrowAny {
            cachedHello1(who)
        }

        cachedHello1(who) shouldBe "hello me"
    }

    describe("should also only throw once") {
        calls.set(0)
        val who = "me"
        shouldThrowAny {
            cachedHello2(who)
        }

        cachedHello2(who) shouldBe "hello me"
    }

})
