package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.common.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnPlattformTokenClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.IdentityProvider
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthConfig
import no.nav.arbeidsgiver.notifikasjon.util.App.`ekstern-varsling`
import no.nav.arbeidsgiver.notifikasjon.util.DevGcpEnv
import java.net.URI

fun main() = runBlocking {
    val gcpEnv = DevGcpEnv(`ekstern-varsling`)
//    val gcpEnv = ProdGcpEnv(`ekstern-varsling`)
    val texasEnv = gcpEnv.getEnvVars("NAIS_TOKEN_")
    URI(texasEnv["NAIS_TOKEN_ENDPOINT"]!!).let { uri ->
        try {
            uri.toURL().openConnection().connect()
            println("""texas is available at $uri""")
        } catch (e: Exception) {
            println("")

            println(
                """
        ######
        # Failed to connect to $uri - ${e.message}
        # 
        # Connecting to altinn 3 via devgcp requires port forwarding for texas.
        #
        # E.g: kubectl port-forward ${gcpEnv.getPods().first()} ${uri.port}
        ######
        
        An attempt at port forward will be made for you now:
        
            """.trimIndent()
            )

            gcpEnv.portForward(uri.port) {
                try {
                    uri.toURL().openConnection().connect()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
    val altinnEnv = gcpEnv.getEnvVars("ALTINN_3")
    val devGcpClient = Altinn3VarselKlientImpl(
        altinnBaseUrl = altinnEnv["ALTINN_3_API_BASE_URL"]!!,
        altinnPlattformTokenClient = AltinnPlattformTokenClientImpl(
            altinnBaseUrl = altinnEnv["ALTINN_3_API_BASE_URL"]!!,
            authClient = AuthClientImpl(
                TexasAuthConfig(
                    tokenEndpoint = texasEnv["NAIS_TOKEN_ENDPOINT"]!!,
                    tokenExchangeEndpoint = texasEnv["NAIS_TOKEN_EXCHANGE_ENDPOINT"]!!,
                    tokenIntrospectionEndpoint = texasEnv["NAIS_TOKEN_INTROSPECTION_ENDPOINT"]!!,
                ),
                IdentityProvider.MASKINPORTEN
            ),
        )
    )

    runBlocking {
        listOf(
            "43719cae-cdcf-4dd1-9e30-8926612dde22"
//            "de3e866c-f88c-4f93-bf48-f349f01a375f"
        ).forEach { orderId ->

            devGcpClient.notifications(orderId).also {
                println("")
                println("notifications orderId $orderId:")
                println(it)
                println("")
            }
        }
    }

}