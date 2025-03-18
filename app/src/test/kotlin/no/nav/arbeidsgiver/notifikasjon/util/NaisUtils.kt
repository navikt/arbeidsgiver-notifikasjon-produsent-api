package no.nav.arbeidsgiver.notifikasjon.util

import io.ktor.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import no.nav.arbeidsgiver.notifikasjon.util.App.`bruker-api`
import no.nav.arbeidsgiver.notifikasjon.util.App.`ekstern-varsling`
import no.nav.arbeidsgiver.notifikasjon.util.NaisAivenKafka.Cmd.`kafka-topics`
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path


const val namespace = "fager"

@Suppress("unused")
enum class Cluster {
    `dev-gcp`,
    `prod-gcp`
}

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

class Proc {
    companion object {
        fun exec(
            cmd: Array<String>,
            envp: Array<String>? = null,
            silent: Boolean = true,
            timeout: Long = 5,
        ): String {
            if (!silent) {
                println(cmd.joinToString(" "))
            }
            val process = Runtime.getRuntime().exec(cmd.toList().toTypedArray<String>(), envp)
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
        fun execBg(cmd: Array<String>): Process {
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
    }
}

class Kubectl(
    cluster: Cluster,
) {
    private val k = arrayOf("kubectl", "--context=$cluster", "--namespace=$namespace")

    private fun kubectl(vararg cmd: String) = arrayOf(*k, *cmd)

    private fun sexec(vararg args: String): String = Proc.exec(kubectl(*args))
    private fun exec(vararg args: String): String = Proc.exec(kubectl(*args), silent = false)
    private fun execBg(vararg args: String): Process = Proc.execBg(kubectl(*args))

    fun portForward(app: App, port: Int, isReady: () -> Boolean) {
        try {
            execBg(
                "port-forward", getPods(app).first(), "$port:$port",
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

    fun getPods(app: App) = sexec(
        "get", "pods", "-l", "app=$app", "-o", "jsonpath={.items[*].metadata.name}"
    ).split(" ")

    fun scale(app: App, replicas: Int) {
        exec(
            "scale", "--replicas=$replicas", "deployment/${app.id}",
        ).let {
            println(it)
        }
    }

    fun findSecret(prefix: String) = sexec(
        "get", "secrets", "-o", "jsonpath={@.items[*].metadata.name}"
    )
        .split(" ")
        .find { it.startsWith(prefix) } ?: error("could not find secret with prefix $prefix")

    fun getSecrets(secretName: String) =
        sexec("get", "secret", secretName, "-o", "jsonpath={@.data}").let {
            Json.decodeFromString<Map<String, String>>(it)
                .mapValues { e -> e.value.decodeBase64String() }
        }

    fun findSecretResourceNames(app: App) = sexec(
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

    fun getEnvVars(app: App, envVarPrefix: String) =
        sexec(
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
}

const val fager_notifikasjon = "fager.notifikasjon"

enum class KafkaPool {
    `nav-prod`,
    `nav-dev`,
}

enum class ConsumerGroupId {
    `bruker-model-builder-2`,
    `dataprodukt-model-builder-3`,
    `ekstern-varsling-model-builder`,
    `kafka-backup-model-builder`,
    `kafka-bq-v1`,
    `reaper-model-builder`,
    `produsent-model-builder`,
    `skedulert-harddelete-model-builder-1`,
    `skedulert-utgatt-model-builder-0`;

    fun App.consumerGroupId(): ConsumerGroupId {
        return when (this) {
            `bruker-api` -> `bruker-model-builder-2`
            App.`bruker-api-writer` -> `bruker-model-builder-2`
            App.dataprodukt -> `dataprodukt-model-builder-3`
            `ekstern-varsling` -> `ekstern-varsling-model-builder`
            App.`kafka-backup` -> `kafka-backup-model-builder`
            App.`kafka-bq` -> `kafka-bq-v1`
            App.`kafka-reaper` -> `reaper-model-builder`
            App.`produsent-api` -> `produsent-model-builder`
            App.`skedulert-harddelete` -> `skedulert-harddelete-model-builder-1`
            App.`skedulert-utgaatt` -> `skedulert-utgatt-model-builder-0`
            App.`replay-validator` -> TODO()
            App.`skedulert-paaminnelse` -> TODO("ephemeral consumer group")
        }
    }
}

class NaisAivenKafka(
    val topic: String = fager_notifikasjon,
    val pool: KafkaPool,
    val app: App,
) {
    val kafkaCliBin = Path(System.getProperty("user.dir"), ".kafka-cli", "bin")

    fun Cmd.sh(vararg args: String) = arrayOf(
        kafkaCliBin.resolve("${this.name}.sh").toAbsolutePath().toString(),
        *args
    )

    enum class Cmd {
        `kafka-consumer-groups`,
        `kafka-topics`,
    }

    val secretName = "notifikasjon-devops-fager"
    val gcpEnv = when (pool) {
        KafkaPool.`nav-prod` -> ProdGcpEnv(app)
        KafkaPool.`nav-dev` -> DevGcpEnv(app)
    }

    val kafkaSecrets = runCatching {
        gcpEnv.findSecret(secretName)
    }.fold(
        onSuccess = {
            Proc.exec(
                arrayOf(
                    "nais", "aiven", "get", "kafka", it, namespace
                ),
                silent = false,
            ).lines().last().trim().let { loc -> Path(loc) }
        },
        onFailure = {
            println("$secretName secrets missing. Attempting to create for you..")
            Proc.exec(
                arrayOf(
                    "nais", "aiven", "create", "-p", "$pool", "kafka", secretName, namespace
                ),
                silent = false,
            ).let { println(it) }
            error("Please try again shortly")
        }
    )

    val kafkaProperties = kafkaSecrets.resolve("kafka.properties")
    val kafkaSecretDotEnv = kafkaSecrets.resolve("kafka-secret.env")
    val kafkaEnv = KafkaDotEnv(kafkaSecretDotEnv)

    fun describeTopic() = Proc.exec(
        `kafka-topics`.sh(
            "--bootstrap-server", kafkaEnv["KAFKA_BROKERS"],
            "--command-config", kafkaProperties.toString(),
            "--topic", topic,
            "--describe",
        ),
        envp = kafkaEnv.envp()
    ).let { println(it) }

}

class KafkaDotEnv(
    path: Path
) {
    val regex = Regex("(?=KAFKA[^\\s=]+=)")
    val env = path.toFile().readText()
        .split(regex)
        .filterNot { it.startsWith("#") }
        .associate {
            it.split("=").let { (k, v) ->
                k.trim() to v.trim().trim('"')
            }
        }

    fun envp() = env.map { "${it.key}=${it.value}" }.toTypedArray()

    operator fun get(key: String): String {
        return env[key]!!
    }

    override fun toString(): String {
        return env.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }


}

abstract class GcpEnv(
    val app: App,
    cluster: Cluster,
) {
    val kubectl = Kubectl(cluster)

    fun portForward(port: Int, isReady: () -> Boolean) = kubectl.portForward(app, port, isReady)
    fun getPods() = kubectl.getPods(app)

    fun getEnvVars(envVarPrefix: String) = kubectl.getEnvVars(app, envVarPrefix)
    fun getSecrets(secretName: String) = kubectl.getSecrets(secretName)
    fun findSecret(prefix: String) = kubectl.findSecret(prefix)
}

class DevGcpEnv(
    app: App
) : GcpEnv(app, Cluster.`dev-gcp`)

class ProdGcpEnv(
    app: App
) : GcpEnv(app, Cluster.`prod-gcp`)

/**
 * noen eksempler på bruk
 */
fun main() {
    NaisAivenKafka(
        pool = KafkaPool.`nav-dev`,
        app = `ekstern-varsling`
    ).describeTopic()

    val devGcp = DevGcpEnv(`ekstern-varsling`)
    val texas = devGcp.getEnvVars("NAIS_TOKEN_")
    println("texas: ")
    println("  $texas")


    val brukerSecrets = DevGcpEnv(`bruker-api`).getSecrets("notifikasjon-bruker-api-secrets")
    println("notifikasjon-bruker-api-secrets: ")
    println("   $brukerSecrets")

}

