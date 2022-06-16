package no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread

object AzureEnv {
    val tenantId = System.getenv("AZURE_APP_TENANT_ID")!!
    val openidTokenEndpoint = System.getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")!!
    val clientId = System.getenv("AZURE_APP_CLIENT_ID")!!
    val openidIssuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER")!!
    val jwk = System.getenv("AZURE_APP_JWK")!!
    val preAuthorizedApps = System.getenv("AZURE_APP_PRE_AUTHORIZED_APPS")!!
}