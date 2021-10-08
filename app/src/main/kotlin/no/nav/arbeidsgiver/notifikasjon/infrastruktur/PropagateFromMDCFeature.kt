package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.util.*
import org.slf4j.MDC

val defaultKeys = emptyList<String>()

class PropagateFromMDCFeature internal constructor(
    val keysToPropagate: List<String>
) {

    /**
     * [PropagateFromMDCFeature] configuration that is used during installation
     */
    class Config {
        /**
         * backing field
         */
        private var _keysToPropagate: MutableList<String> = defaultKeys.toMutableList()
        /**
         * list of keys to propagate from MDC to request header
         *
         * Default value for [keysToPropagate] is [defaultKeys].
         */
        var keysToPropagate: List<String>
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
            _keysToPropagate += keys
        }
    }

    /**
     * Companion object for feature installation
     */
    @Suppress("EXPERIMENTAL_API_USAGE_FUTURE_ERROR")
    companion object Feature : HttpClientFeature<Config, PropagateFromMDCFeature> {
        override val key: AttributeKey<PropagateFromMDCFeature> = AttributeKey("PropagateFromMDCFeature")

        override fun prepare(block: Config.() -> Unit): PropagateFromMDCFeature {
            val config = Config().apply(block)
            return PropagateFromMDCFeature(config.keysToPropagate)
        }

        override fun install(feature: PropagateFromMDCFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Phases.State) {
                feature.keysToPropagate.forEach { keyToPropagate ->
                    MDC.get(keyToPropagate)?.let { mdcValue ->
                        if (context.headers[keyToPropagate] == null) {
                            context.headers.append(keyToPropagate, mdcValue)
                        }
                    }
                }
                proceed()
            }

        }
    }
}