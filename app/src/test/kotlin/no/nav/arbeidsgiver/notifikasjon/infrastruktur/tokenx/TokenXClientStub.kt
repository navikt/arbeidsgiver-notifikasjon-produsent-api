package no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx

class TokenXClientStub : TokenXClient {
    override suspend fun exchange(subjectToken: String, audience: String): String = "fake tokenx token.."
}