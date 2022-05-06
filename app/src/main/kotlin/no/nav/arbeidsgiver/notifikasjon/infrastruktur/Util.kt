package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import no.nav.arbeidsgiver.notifikasjon.tid.LokalOsloTid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.pow
import kotlin.reflect.KProperty

fun <T> basedOnEnv(
    prod: () -> T,
    other: () -> T,
    dev: () -> T = other,
): T =
    when (System.getenv("NAIS_CLUSTER_NAME")) {
        "prod-gcp" -> prod()
        "dev-gcp" -> dev()
        else -> other()
    }


/** Get logger for enclosing class. */
inline fun <reified T : Any> T.logger(): Logger =
    LoggerFactory.getLogger(this::class.java)

fun Int.toThePowerOf(exponent: Int): Long = toDouble().pow(exponent).toLong()


/** Make a field unavaiable in production.
 *
 * If a field is not supposed to be be available in production, but for instance when
 * running on developer machine or in the development cluster, this delegated field
 * ensures that any attemted read will fail in prod.
 *
 */
class UnavailableInProduction<T>(initializer: () -> T) {
    private val cluster: String = System.getenv("NAIS_CLUSTER_NAME") ?: ""
    private val value: T by lazy(initializer)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (cluster == "prod-gcp") {
            throw Error(
                """
                Attempt at accessing property '${property.name}' in class '${thisRef?.javaClass?.canonicalName}' denied.
                This field is not available in cluster 'prod-gcp'.
                """.trimIndent()
            )
        } else {
            return value
        }
    }
}
class UnassignableInProduction<T>(initializer: () -> T) {
    private val cluster: String = System.getenv("NAIS_CLUSTER_NAME") ?: ""
    private var value: T = initializer()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        if (cluster == "prod-gcp") {
            throw Error(
                """
                Attempt at assign property '${property.name}' in class '${thisRef?.javaClass?.canonicalName}' denied.
                This field is not available for assignment in cluster 'prod-gcp'.
                """.trimIndent()
            )
        } else {
            value = newValue
        }
    }
}
