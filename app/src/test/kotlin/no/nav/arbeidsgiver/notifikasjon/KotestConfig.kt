package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.config.AbstractProjectConfig

@Suppress("unused") /* Automatically picked up by kotest */
object KotestConfig: AbstractProjectConfig() {
    override val parallelism: Int = 4
}