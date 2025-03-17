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
    val app = `ekstern-varsling`
    val eksternVarslingEnv = DevGcpEnv.using(app)
    val texasEnv = eksternVarslingEnv.getEnvVars("NAIS_TOKEN_")
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
        # E.g: kubectl port-forward ${eksternVarslingEnv.getPods().first()} ${uri.port}
        ######
        
        An attempt at port forward will be made for you now:
        
            """.trimIndent()
            )

            eksternVarslingEnv.portForward(uri.port) {
                try {
                    uri.toURL().openConnection().connect()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
    val altinnEnv = eksternVarslingEnv.getEnvVars("ALTINN_3")
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

    val smsTest = "ee9e6dd5-64e1-4e2d-a71b-35623979e20f"
    val resourceIdTest = "7a9b8646-cbb7-4f98-b026-869fa4f0cbd8"
    val emailTest = "64bd510b-28f8-40d9-9f24-d26952e75840"
    val resourceIdTest2 = "95fb37bd-cbff-4a9d-84e1-b5bdd9a8981c"
    runBlocking {
        listOf(
            smsTest,
            resourceIdTest,
            emailTest,
            resourceIdTest2,
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