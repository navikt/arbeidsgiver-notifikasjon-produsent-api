package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.listeners.TestListener
import io.kotest.core.source.SourceRef
import io.kotest.core.spec.AutoScan
import io.kotest.core.test.TestCase
import io.kotest.matchers.string.shouldContain

/**
 * Vi har opplevd at tester vi kjører lokalt ikke blir kjørt av maven pga surefire forventer at filnavn inneholder Test
 * Denne lytteren skal forhåpentligvis hjelpe oss unngå det ved å fange det opp ved lokal kjøring i idea/kotest plugin.
 */
@AutoScan
object EnsureTestFileNameListener : TestListener {
    override suspend fun beforeAny(testCase: TestCase) {
        /**
         * er noe rart med fileName når man har forAll/withData med suspend. blir satt til
         * testCase.source.fileName == "ContinuationImpl.kt"
         *
         * Derfor sjekker vi bare når vi er på root, der er det sannsynligvis ikke et problem
         */
        if (testCase.descriptor.isRootTest()) {
            when(val source = testCase.source) {
                is SourceRef.FileSource -> source.fileName shouldContain "Test"
                is SourceRef.ClassSource -> source.fqn shouldContain "Test"
                is SourceRef.None -> {}
            }
        }
    }
}