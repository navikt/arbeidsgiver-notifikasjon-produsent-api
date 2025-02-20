package no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas

open class AuthClientStub : AuthClient {
    override suspend fun token(target: String): TokenResponse = TokenResponse.Success("fake token", 3600)
    override suspend fun exchange(target: String, userToken: String): TokenResponse = TokenResponse.Success("fake token", 3600)
    override suspend fun introspect(accessToken: String): TokenIntrospectionResponse = TokenIntrospectionResponse(true, null)
}