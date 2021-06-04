package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.*
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Mottaker

typealias Merkelapp = String

data class ProdusentDefinisjon(
    val id: String,
    val merkelapper: List<Merkelapp> = emptyList(),
    val mottakere: List<MottakerDefinisjon> = emptyList()
) {
    fun harTilgangTil(merkelapp: String): Boolean {
        return merkelapper.contains(merkelapp)
    }

    fun harTilgangTil(mottaker: Mottaker): Boolean {
        return when (mottaker) {
            is AltinnMottaker -> mottakere.filterIsInstance<Servicecode>().any {
                mottaker.altinntjenesteKode == it.code
                        && mottaker.altinntjenesteVersjon == it.version
            }
            else -> true // TODO: fnr mottaker validering er ikke implementert enda
        }

    }
}

private inline fun <reified T> ObjectMapper.readResource(name: String): T {
    return objectMapper.readValue(this.javaClass.getResource(name)!!)
}

object ProdusentRegister {
    private val REGISTER: List<ProdusentDefinisjon> = objectMapper.readResource("/produsent-register.json")
    fun finn(subject: String): ProdusentDefinisjon {
        val produsent = REGISTER.find { it.id == subject } ?: ProdusentDefinisjon(subject)
        validate(produsent)
        return produsent
    }

    private fun validate(produsentDefinisjon: ProdusentDefinisjon) {
        produsentDefinisjon.mottakere.forEach {
            when (it) {
                is Servicecode -> check(MottakerRegister.erDefinert(it)) {
                    "Ugyldig mottaker $it for produsent $produsentDefinisjon"
                }
            }
        }
    }

    fun validateAll() = REGISTER.forEach(this::validate)
}

object MottakerRegister {
    private val REGISTER = objectMapper.readResource<List<MottakerDefinisjon>>("/mottaker-register.json")

    val servicecodes: List<Servicecode>
        get() {
            return REGISTER.filterIsInstance<Servicecode>()
        }

    fun erDefinert(servicecode: Servicecode): Boolean = servicecodes.contains(servicecode)
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class MottakerDefinisjon

@JsonTypeName("altinn")
data class Servicecode(
    val code: String,
    val version: String,
    val description: String?
) : MottakerDefinisjon() {
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
