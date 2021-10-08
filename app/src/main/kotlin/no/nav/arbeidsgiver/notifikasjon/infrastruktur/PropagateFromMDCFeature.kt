package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import org.slf4j.MDC

public val defaultKeys = emptyList<String>()

public class PropagateFromMDCFeature internal constructor(
    public val keysToPropagate: List<String>
) {
    internal constructor(config: Config) : this(
        config.keysToPropagate
    )

    /**
     * [PropagateFromMDCFeature] configuration that is used during installation
     */
    public class Config {
        /**
         * backing field
         */
        private var _keysToPropagate: MutableList<String> = defaultKeys.toMutableList()
        /**
         * list of keys to propagate from MDC to request header
         *
         * Default value for [keysToPropagate] is [defaultKeys].
         */
        public var keysToPropagate: List<String>
            set(value) {
                require(value.isNotEmpty()) { "At least one key should be provided" }

                _keysToPropagate.clear()
                _keysToPropagate.addAll(value)
            }
            get() = _keysToPropagate

        /**
         * Adds key to propagate
         */
        public fun propagate(vararg keys: String) {
            _keysToPropagate += keys
        }
    }

    /**
     * Companion object for feature installation
     */
    @Suppress("EXPERIMENTAL_API_USAGE_FUTURE_ERROR")
    public companion object Feature : HttpClientFeature<Config, PropagateFromMDCFeature> {
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