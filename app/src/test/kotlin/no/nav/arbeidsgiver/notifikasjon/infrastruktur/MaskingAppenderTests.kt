package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
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

        it("works altinn error message") {
            mask(
                "The ReceiverAddress/User profile must contain a valid emailaddress. Address: julenissen@nordpoolen.no, User: 123123123"
            ) shouldBe """
                The ReceiverAddress/User profile must contain a valid emailaddress. Address: ********, User: *********
            """.trimIndent()
        }
    }
})

/*
Ikke-retryable feil fra altinn ved sending av notifikasjon: AltinnResponse.Feil(
                    altinnErrorMessage=The ReceiverAddress/User profile must contain a valid emailaddress. Address: merete@appoint-.no, User: 995536021
                    altinnExtendedErrorMessage=No information available
                    altinnLocalizedErrorMessage=The ReceiverAddress/User profile must contain a valid emailaddress. Address: merete@appoint-.no, User: 995536021
                    errorGuid=06722395-14dd-4fa3-a789-882553ded9aa
                    errorID=30010
                    userGuid=-no value-
                    userId=0
                ):
 */