package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.TokenExchangeClient

class TokenExchangeClientStub : TokenExchangeClient {
    override suspend fun exchangeToken(subjectToken: String, targetAudience: String): String {
        return subjectToken
    }
}