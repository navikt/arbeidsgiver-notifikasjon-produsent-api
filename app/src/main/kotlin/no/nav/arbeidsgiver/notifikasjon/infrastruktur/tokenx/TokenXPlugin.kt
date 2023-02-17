package no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

private const val BEARER_PREFIX = "Bearer "

class TokenXPlugin internal constructor(
    val audience: String,
    val tokenXClient: TokenXClient,
) {

    class Config {
        var audience: String? = null
            set(value) {
                require(audience == null) {
                    "audience already set"
                }
                field = value
            }
        var tokenXClient: TokenXClient? = null
            set(value) {
                require(tokenXClient == null) {
                    "tokenXClient already set"
                }
                field = value
            }
    }

    companion object Plugin : HttpClientPlugin<Config, TokenXPlugin> {
        private val log = logger()

        override val key: AttributeKey<TokenXPlugin> = AttributeKey("TokenXPlugin")

        override fun prepare(block: Config.() -> Unit): TokenXPlugin {
            val config = Config().apply(block)
            return TokenXPlugin(
                audience = requireNotNull(config.audience) {
                    "TokenXPlugin missing audience. Please configure audience."
                },
                tokenXClient = config.tokenXClient ?: TokenXClientImpl()
            )
        }

        override fun install(plugin: TokenXPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Phases.State) {
                val subjectToken = context.headers[HttpHeaders.Authorization]?.removePrefix(BEARER_PREFIX)

                if (subjectToken == null) {
                    log.warn("subjectToken not present. skipping exchange for audience {}", plugin.audience)
                } else {
                    try {
                        val accessToken = plugin.tokenXClient.exchange(subjectToken, plugin.audience)
                        context.bearerAuth(accessToken)
                    } catch (e: RuntimeException) {
                        log.warn("failed to set bearer auth header due to exception in exchange ${e.message}", e)
                    }
                }

                proceed()
            }

        }
    }
}