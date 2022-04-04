package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnConfig as ClientAltinnConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv


object AltinnConfig {
    private const val PROXY_URL = "http://altinn-rettigheter-proxy.arbeidsgiver/altinn-rettigheter-proxy/"

    private const val CONSUMER_ID = "notifikasjon-bruker-api"

    val ALTINN_ORIGIN by lazy {
        basedOnEnv(
            prod = { "https://api-gw.oera.no" },
            other = { "https://api-gw-q1.oera.no" },
        )
    }

    val ALTINN_KEY by lazy {
        System.getenv("ALTINN_HEADER") ?: "default"
    }
    val GW_KEY by lazy {
        System.getenv("APIGW_HEADER") ?: "default"
    }

    val config by lazy {
        AltinnrettigheterProxyKlientConfig(
            ProxyConfig(
                url = PROXY_URL,
                consumerId = CONSUMER_ID,
            ),
            ClientAltinnConfig(
                url = ALTINN_ORIGIN,
                altinnApiKey = ALTINN_KEY,
                altinnApiGwApiKey = GW_KEY,
            )
        )
    }
}

fun defaultBlockingAltinnClient() = AltinnrettigheterProxyKlient(AltinnConfig.config)

