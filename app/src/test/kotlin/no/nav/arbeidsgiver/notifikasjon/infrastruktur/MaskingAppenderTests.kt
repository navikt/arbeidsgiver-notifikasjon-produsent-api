package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.contain
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging.MaskingAppender.Companion.mask

class MaskingAppenderTests: DescribeSpec({
    describe("Masking fødselsnummer") {
        it("works") {
            mask("At nummer 11223344556") shouldNot contain("11223344556")
        }
    }
})