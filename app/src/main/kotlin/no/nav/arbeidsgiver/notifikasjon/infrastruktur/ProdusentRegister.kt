package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.module.kotlin.*

typealias Merkelapp = String

data class Produsent(
    val id: String,
    val merkelapper: List<Merkelapp> = emptyList(),
    val mottakere: List<Mottaker> = emptyList()
) {
    fun har(merkelappp: String): Boolean {
        return merkelapper.contains(merkelappp)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Mottaker

object ProdusentRegister {
    val json = this.javaClass.getResource("/produsent-register.json")!!
    private val register: List<Produsent> = objectMapper.readValue(json)
    fun finn(subject: String): Produsent {
        val produsent = register.find { it.id == subject } ?: Produsent(subject)
        validate(produsent)
        return produsent
    }

    private fun validate(produsent: Produsent) {
        produsent.mottakere.forEach {
            when (it) {
                is Servicecode -> check(MottakerRegister.erDefinert(it)) {
                    "Ugyldig mottaker $it for produsent $produsent"
                }
            }
        }
    }

    fun validateAll() = register.forEach(this::validate)
}

@JsonTypeName("altinn")
data class Servicecode(
    val code: String,
    val version: String,
    val description: String?
) : Mottaker() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Servicecode

        if (code != other.code) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }
}

object MottakerRegister {
    val json = this.javaClass.getResource("/mottaker-register.json")!!
    private val register = objectMapper.readValue<List<Mottaker>>(json)

    fun hentAlleServicecodes(): List<Servicecode> {
        return register.filterIsInstance<Servicecode>()
    }

    fun erDefinert(servicecode: Servicecode): Boolean {
        return register.contains(servicecode)
    }
}