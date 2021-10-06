package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.contain
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging.MaskingAppender.Companion.mask

class MaskingAppenderTests: DescribeSpec({
    describe("Masking fødselsnummer") {
        it("works for 11 digits") {
            mask("At nummer 11223344556") shouldNot contain("11223344556")
        }

        it("works 12 numbers") {
            mask("At nummer 112233445566") should contain("112233445566")
        }

        it("works fnr leading") {
            mask("At nummer x11223344556") shouldNot contain("11223344556")
        }

        it("works fnr followed by char") {
            mask("At nummer 11223344556x") shouldNot contain("11223344556")
        }

        it("works fnr:") {
            mask("fnr:11223344556") shouldNot contain("11223344556")
        }

        it("works fnr=") {
            mask("fnr=11223344556") shouldNot contain("11223344556")
        }
    }
})