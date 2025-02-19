package no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.coRecord
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.defaultHttpClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.withTimer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

/**
 * Lånt med modifikasjoner fra https://github.com/nais/wonderwalled
 */

enum class IdentityProvider(val alias: String) {
    MASKINPORTEN("maskinporten"),
    AZURE_AD("azuread"),
    IDPORTEN("idporten"),
    TOKEN_X("tokenx"),
}

@JsonIgnoreProperties(ignoreUnknown = true)
sealed class TokenResponse {
    data class Success(
        @JsonProperty("access_token")
        val accessToken: String,
        @JsonProperty("expires_in")
        val expiresInSeconds: Int,
    ) : TokenResponse() {
        override fun toString() = "TokenResponse.Success(accessToken: SECRET, expiresInSeconds: $expiresInSeconds)"
    }

    data class Error(
        val error: TokenErrorResponse,
        val status: HttpStatusCode,
    ) : TokenResponse()

    fun isSuccess() = this is Success
    fun isError() = this is Error
    fun <R> fold(onSuccess: (Success) -> R, onError: (Error) -> R): R =
        when (this) {
            is Success -> onSuccess(this)
            is Error -> onError(this)
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenErrorResponse(
    val error: String,
    @JsonProperty("error_description")
    val errorDescription: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenIntrospectionResponse(
    val active: Boolean,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val error: String?,

    val acr: String? = null,
    val pid: String? = null,
    val azp: String? = null,
    /**
     * mapet "other" er alltid tomt. Dette er pga @JsonAnySetter er broken i jackson 2.17.0+
     * @JsonAnySetter er fikset i 2.18.1 https://github.com/FasterXML/jackson-databind/issues/4508
     */
    //@JsonAnySetter @get:JsonAnyGetter
    //val other: Map<String, Any?> = mutableMapOf(),
)

data class TexasAuthConfig(
    val tokenEndpoint: String,
    val tokenExchangeEndpoint: String,
    val tokenIntrospectionEndpoint: String,
) {
    companion object {
        fun nais() = TexasAuthConfig(
            tokenEndpoint = System.getenv("NAIS_TOKEN_ENDPOINT"),
            tokenExchangeEndpoint = System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT"),
            tokenIntrospectionEndpoint = System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT"),
        )
    }
}

interface AuthClient {
    suspend fun token(target: String): TokenResponse

    suspend fun exchange(target: String, userToken: String): TokenResponse

    suspend fun introspect(accessToken: String): TokenIntrospectionResponse
}

class AuthClientImpl(
    private val config: TexasAuthConfig,
    private val provider: IdentityProvider,
    private val httpClient: HttpClient = defaultHttpClient(),
) : AuthClient {

    override suspend fun token(target: String): TokenResponse = try {
        withTimer("texas_auth_token").coRecord {
            httpClient.submitForm(config.tokenEndpoint, parameters {
                set("target", target)
                set("identity_provider", provider.alias)
            }).body<TokenResponse.Success>()
        }
    } catch (e: ResponseException) {
        TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
    }

    override suspend fun exchange(target: String, userToken: String): TokenResponse = try {
        withTimer("texas_auth_exchange").coRecord {
            httpClient.submitForm(config.tokenExchangeEndpoint, parameters {
                set("target", target)
                set("user_token", userToken)
                set("identity_provider", provider.alias)
            }).body<TokenResponse.Success>()
        }
    } catch (e: ResponseException) {
        TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
    }

    override suspend fun introspect(accessToken: String) : TokenIntrospectionResponse = withTimer("texas_auth_introspect").coRecord {
        httpClient.submitForm(config.tokenIntrospectionEndpoint, parameters {
            set("token", accessToken)
            set("identity_provider", provider.alias)
        }).body()
    }
}


val TexasAuth = createRouteScopedPlugin(
    name = "TexasAuthPlugin",
    createConfiguration = ::TexasAuthPluginConfiguration,
) {
    val log = logger()
    val client = pluginConfig.client ?: throw IllegalArgumentException("TexasAuth plugin: client must be set")
    val validate = pluginConfig.validate ?: throw IllegalArgumentException("TexasAuth plugin: validate must be set")

    val challenge: suspend (ApplicationCall, AuthenticationFailedCause) -> Unit = { call, cause ->
        when (cause) {
            is AuthenticationFailedCause.Error -> log.error("unauthenticated: ${cause.message}")
            is AuthenticationFailedCause.NoCredentials -> log.error("unauthenticated: NoCredentials") // should not happen
            is AuthenticationFailedCause.InvalidCredentials -> log.info("unauthenticated: InvalidCredentials")
        }

        call.respondNullable(HttpStatusCode.Unauthorized)
    }

    pluginConfig.apply {
        onCall { call ->
            val token = call.bearerToken()
            if (token == null) {
                challenge(call, AuthenticationFailedCause.NoCredentials)
                return@onCall
            }

            val introspectResponse = try {
                client.introspect(token)
            } catch (e: Exception) {
                challenge(call, AuthenticationFailedCause.Error(e.message ?: "introspect request failed"))
                return@onCall
            }

            if (!introspectResponse.active) {
                challenge(call, AuthenticationFailedCause.InvalidCredentials)
                return@onCall
            }

            val principal = validate(introspectResponse)
            if (principal == null) {
                challenge(call, AuthenticationFailedCause.InvalidCredentials)
                return@onCall
            }

            call.authentication.principal(principal)
            return@onCall
        }
    }

    log.info("TexasAuth plugin loaded.")
}

class TexasAuthPluginConfiguration(
    var client: AuthClient? = null,
    var validate: ((TokenIntrospectionResponse) -> Principal?)? = null,
)

class TexasAuthClientPluginConfig(
    var authClient: AuthClient? = null,
    var fetchToken: (suspend (AuthClient) -> TokenResponse)? = null,
)

val TexasAuthClientPlugin = createClientPlugin("TexasAuthClientPlugin", ::TexasAuthClientPluginConfig) {
    val authClient = requireNotNull(pluginConfig.authClient) {
        "TexasAuthClientPlugin: property 'authClient' must be set in configuration when installing plugin"
    }
    val fetchToken = requireNotNull(pluginConfig.fetchToken) {
        "TexasAuthClientPlugin: property 'fetchToken' must be set in configuration when installing plugin"
    }

    onRequest { request, _ ->
        when (val token = fetchToken(authClient)) {
            is TokenResponse.Success -> request.bearerAuth(token.accessToken)
            is TokenResponse.Error -> throw Exception("Failed to fetch token: ${token.error}")
        }
    }
}

fun ApplicationCall.bearerToken(): String? =
    request.authorization()
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.removePrefix("Bearer ")
        ?.removePrefix("bearer ")