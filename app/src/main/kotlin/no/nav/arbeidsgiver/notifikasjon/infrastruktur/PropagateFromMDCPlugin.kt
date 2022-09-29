package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*
import org.slf4j.MDC

val defaultKeys = emptyList<PropagatedKey>()

data class PropagatedKey(
    val mdcKey: String,
    val headerKey: String
)

infix fun String.asHeader(that: String): PropagatedKey = PropagatedKey(mdcKey = this, headerKey = that)

class PropagateFromMDCPlugin internal constructor(
    val keysToPropagate: List<PropagatedKey>
) {

    /**
     * [PropagateFromMDCPlugin] configuration that is used during installation
     */
    class Config {
        /**
         * backing field
         */
        private var _keysToPropagate: MutableList<PropagatedKey> = defaultKeys.toMutableList()
        /**
         * list of keys to propagate from MDC to request header
         *
         * Default value for [keysToPropagate] is [defaultKeys].
         */
        var keysToPropagate: List<PropagatedKey>
            set(value) {
                require(value.isNotEmpty()) { "At least one key should be provided" }

                _keysToPropagate.clear()
                _keysToPropagate.addAll(value)
            }
            get() = _keysToPropagate

        /**
         * Adds key to propagate
         */
        fun propagate(vararg keys: String) {
            _keysToPropagate += keys.map { it asHeader it }
        }

        /**
         * Adds key to propagate
         */
        fun propagate(vararg keys: PropagatedKey) {
            _keysToPropagate += keys
        }
    }

    /**
     * Companion object for feature installation
     */
    companion object Plugin : HttpClientPlugin<Config, PropagateFromMDCPlugin> {
        override val key: AttributeKey<PropagateFromMDCPlugin> = AttributeKey("PropagateFromMDCPlugin")

        override fun prepare(block: Config.() -> Unit): PropagateFromMDCPlugin {
            val config = Config().apply(block)
            return PropagateFromMDCPlugin(config.keysToPropagate)
        }

        override fun install(plugin: PropagateFromMDCPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Phases.State) {
                plugin.keysToPropagate.forEach { keyToPropagate ->
                    MDC.get(keyToPropagate.mdcKey)?.let { mdcValue ->
                        if (context.headers[keyToPropagate.headerKey] == null) {
                            context.headers.append(keyToPropagate.headerKey, mdcValue)
                        }
                    }
                }
                proceed()
            }

        }
    }
}