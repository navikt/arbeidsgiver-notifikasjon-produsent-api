package no.nav.arbeidsgiver.notifikasjon.util

import io.ktor.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import no.nav.arbeidsgiver.notifikasjon.util.App.`bruker-api`
import no.nav.arbeidsgiver.notifikasjon.util.App.`ekstern-varsling`
import no.nav.arbeidsgiver.notifikasjon.util.DevGcpEnv.Companion.findResourceName
import no.nav.arbeidsgiver.notifikasjon.util.DevGcpEnv.Companion.getSecrets
import java.util.concurrent.TimeUnit

/**
 * denne klassen inneholder et sett med utility funksjoner som gjør det mulig å hente ut secrets og env-vars fra
 * kubernetes i dev-gcp clusteret.
 */

@Suppress("unused")
enum class App(val id: String) {
    `bruker-api`("notifikasjon-bruker-api"),
    `bruker-api-writer`("notifikasjon-bruker-api-writer"),
    dataprodukt("notifikasjon-dataprodukt"),
    `ekstern-varsling`("notifikasjon-ekstern-varsling"),
    `kafka-backup`("notifikasjon-kafka-backup"),
    `kafka-bq`("notifikasjon-kafka-bq"),
    `kafka-reaper`("notifikasjon-kafka-reaper"),
    `produsent-api`("notifikasjon-produsent-api"),
    `replay-validator`("notifikasjon-replay-validator"),
    `skedulert-harddelete`("notifikasjon-skedulert-harddelete"),
    `skedulert-paaminnelse`("notifikasjon-skedulert-paaminnelse"),
    `skedulert-utgaatt`("notifikasjon-skedulert-utgaatt"), ;

    override fun toString() = id
}

class DevGcpEnv(
    val app: App
) {

    val secretResourceNames = findSecretResourceNames(app)
    fun getEnvVars(envVarPrefix: String) = getEnvVars(app, envVarPrefix)
    fun findResourceName(prefix: String) = findResourceName(app, prefix)
    fun getPods() = getPods(app)

    fun portForward(port: Int, isReady: () -> Boolean) {
        try {
            execBg(
                *kubectl,
                "port-forward", getPods().first(), "$port:$port",
            ).let {
                Runtime.getRuntime().addShutdownHook(object : Thread() {
                    override fun run() {
                        it.destroy()
                    }
                })
            }
            var attempts = 0
            while (!isReady() && attempts <= 5) {
                println("waiting for port forwarding to be available")
                attempts += 1
                Thread.sleep(200)
            }
        } catch (e: Exception) {
            println("Error running process: ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        fun using(app: App) = DevGcpEnv(app)

        private val kubectl = arrayOf("kubectl", "--context=dev-gcp", "--namespace=fager")
        private fun exec(vararg cmd: String): String {
            val timeout = 5L
            val process = Runtime.getRuntime().exec(cmd.toList().toTypedArray<String>())
            try {
                if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                    error(
                        """
                executing command timed out after $timeout seconds.
                command: ${cmd.joinToString(" ")}
                """.trimIndent()
                    )
                }
                val exit = process.exitValue()
                val stderr = process.errorReader().readText()
                val stdout = process.inputReader().readText()
                if (exit != 0) {
                    error(
                        """
                command failed (exit value $exit): ${cmd.joinToString(" ")}
                stdout:
                ${stdout.prependIndent()}
                stderr:
                ${stderr.prependIndent()}
            """.trimIndent()
                    )
                }
                return stdout
            } finally {
                process.destroy()
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        private fun execBg(vararg cmd: String): Process {
            println(cmd.joinToString(" "))
            val process = Runtime.getRuntime().exec(cmd.toList().toTypedArray<String>())
            GlobalScope.launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { println(it) }
                }
            }
            GlobalScope.launch(Dispatchers.IO) {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { println(it) }
                }
            }
            return process
        }

        fun findSecretResourceNames(app: App) = exec(
            *kubectl,
            "get", "pods", "-l", "app=$app",
            "-o", """jsonpath=
        {'{'}
            "envFrom": { .items[0].spec.containers[?(@.name=="$app")].envFrom },
            "env": { .items[0].spec.containers[?(@.name=="$app")].env }
        {'}'}
        """.trimMargin()
        ).let { json ->
            Json.decodeFromString<JsonElement>(json).jsonObject.let {
                it["envFrom"]!!.jsonArray.map { envFrom -> envFrom.jsonObject["secretRef"]!!.jsonObject["name"]!!.jsonPrimitive.content }
            }
        }

        fun findResourceName(app: App, prefix: String) = requireNotNull(
            findSecretResourceNames(app).find { it.startsWith(prefix) }
        ) {
            "can't find $prefix secrets :'("
        }

        fun getEnvVars(app: App, envVarPrefix: String) =
            exec(
                *kubectl,
                "get",
                "deployment",
                app.id,
                "-o",
                "jsonpath={@.spec.template.spec.containers[?(@.name=='$app')].env}"
            ).let {
                Json.decodeFromString<List<Map<String, JsonElement>>>(it)
                    .filter { entries ->
                        entries.containsKey("value")
                                && entries["name"]?.jsonPrimitive?.content?.startsWith(envVarPrefix) == true
                    }
                    .associate { entries ->
                        entries["name"]?.jsonPrimitive?.content to entries["value"]?.jsonPrimitive?.content
                    }
            }

        fun getSecrets(secretName: String) =
            exec(*kubectl, "get", "secret", secretName, "-o", "jsonpath={@.data}").let {
                Json.decodeFromString<Map<String, String>>(it)
                    .mapValues { e -> e.value.decodeBase64String() }
            }

        fun getPods(app: App) = exec(
            *kubectl,
            "get", "pods", "-l", "app=$app",
            "-o", "jsonpath={.items[*].metadata.name}"
        ).split(" ")
    }
}

/**
 * noen eksempler på bruk
 */
fun main() {

    val eksternVarslingEnv = DevGcpEnv.using(`ekstern-varsling`)
    val texas = eksternVarslingEnv.getEnvVars("NAIS_TOKEN_")
    println("texas: ")
    println("  $texas")


    val brukerSecrets = getSecrets(findResourceName(`bruker-api`, "notifikasjon-bruker-api-secrets"))
    println("notifikasjon-bruker-api-secrets: ")
    println("   $brukerSecrets")
}

