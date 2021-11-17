package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.AutoScan
import io.kotest.core.test.TestCase
import io.kotest.matchers.string.shouldContain

/**
 * Vi har opplevd at tester vi kjører lokalt ikke blir kjørt av maven pga surefire forventer at filnavn inneholder Test
 * Denne lytteren skal forhåpentligvis hjelpe oss unngå det ved å fange det opp ved lokal kjøring.
 */
@AutoScan
object EnsureTestFileNameListener : TestListener {
    override suspend fun beforeAny(testCase: TestCase) {
        testCase.source.fileName shouldContain "Test"
    }
}