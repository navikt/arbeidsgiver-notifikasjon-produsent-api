package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

class SimpleLRUCacheTests: DescribeSpec({
    describe("lru cache") {
        val callCounter = AtomicInteger()

        val cache = FunkyCache<String, Int>(100) {
            delay(10)
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

        it("pararell calls with same key, one side effect") {
            val calls = (1..2).map {
                async {
                    cache.get("trololol")
                }
            }
            val results = calls.awaitAll()

            callCounter.get() shouldBe 3 // not 4
            results shouldBe listOf(0, 0)
        }
    }
})