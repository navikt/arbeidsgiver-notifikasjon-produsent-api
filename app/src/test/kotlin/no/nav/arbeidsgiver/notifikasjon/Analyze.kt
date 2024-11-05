package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import java.io.File
import java.net.URLDecoder
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

/**
 * Analyserer kodebasen for funksjoner som tar inn en subklasse av Hendelse.
 * Nyttig for å kartlegge alle modeller som håndterer spesifikke hendelser.
 *
 * Avhengig av at klassene som agerer på hendelse har subklassen med i signaturen til funksjonen som behandler hendelsen.
 * Dersom argument er av typen Hendelse og ser vi ikek her hvilke konkrete hendelser som blir behandlet.
 *
 * TODO oppdater alle funksjoner til å ta inn konkrete hendelser slik at vi lett kan lage en oversikt ved å kjøre dette verktøyet.
 */
fun main() {
    val results = findClassesWithFunctionTakingSealedSubclass("no.nav.arbeidsgiver.notifikasjon", HendelseModel.Hendelse::class)

    val groupedResults = results.groupBy { it.first.qualifiedName?.substringBeforeLast(".") ?: "" }
    groupedResults.forEach { (pkg, functions) ->
        println("")
        println(pkg)
        functions.groupBy { it.first.simpleName }.forEach { (clazz, functions) ->
            println("  $clazz")
            functions.forEach { (_, function, hendelse) ->
                println("    ${function.name}( ${hendelse.simpleName} )")
            }
        }
    }
}

fun findClassesWithFunctionTakingSealedSubclass(
    packageName: String,
    sealedType: KClass<*>
): List<Triple<KClass<*>, KFunction<*>, KClass<*>>> {
    val classes = getClasses(packageName).filterNot {
        "test" in (it.toString().lowercase(Locale.getDefault())) ||
                "stub" in (it.toString().lowercase(Locale.getDefault()))
    }

    return classes.flatMap { clazz ->
        clazz.functions.mapNotNull { function ->
            val parametersToCheck = function.parameters
                .drop(1)
                .let { params ->
                    if (function.isSuspend) params.dropLast(1) else params // Ignore `Continuation` for suspend
                }

            parametersToCheck.firstOrNull { param ->
                try {
                    param.type.jvmErasure.isSubclassOf(sealedType)
                } catch (e: Throwable) {
                    //e.printStackTrace()
                    false // Ignore errors
                }
            }?.let { param ->
                Triple(clazz, function, param.type.jvmErasure)
            }
        }
    }
}

fun getClasses(packageName: String): List<KClass<*>> {
    val path = packageName.replace('.', '/')
    val classLoader = Thread.currentThread().contextClassLoader
    val resources = classLoader.getResources(path)
    val dirs = resources.toList()

    val classes = mutableListOf<Class<*>>()
    for (directory in dirs) {
        classes.addAll(findClasses(File(URLDecoder.decode(directory.file, "UTF-8")), packageName))
    }
    return classes.map { it.kotlin }
}

fun findClasses(directory: File, packageName: String): List<Class<*>> {
    val classes = mutableListOf<Class<*>>()
    if (!directory.exists()) return classes

    for (file in directory.listFiles()!!) {
        if (file.isDirectory) {
            classes.addAll(findClasses(file, "$packageName.${file.name}"))
        } else if (file.name.endsWith(".class")) {
            val className = "$packageName.${file.name.removeSuffix(".class")}"
            try {
                classes.add(Class.forName(className))
            } catch (e: Throwable) {
                //e.printStackTrace()
            }
        }
    }
    return classes
}