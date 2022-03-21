package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger

class SimpleLRUCacheTests: DescribeSpec({
    describe("lru cache") {
        val callCounter = AtomicInteger()

        val cache = SimpleLRUCache<String, Int>(100) {
            callCounter.incrementAndGet()
            0
        }

        it("initially no calls") {
            callCounter.get() shouldBe 0
        }

        it("one call, one side effect")  {
            cache.get("hello")
            callCounter.get() shouldBe 1
        }

        it("call with same key, no extra side effect") {
            cache.get("hello")
            callCounter.get() shouldBe 1
        }

        it("call with different key, new side effect") {
            cache.get("world")
            callCounter.get() shouldBe 2
        }
    }
})