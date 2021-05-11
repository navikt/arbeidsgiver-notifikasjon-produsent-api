package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.module.kotlin.*

data class Produsent(val produsent: String, val merkelapper: List<String>) {
    fun har(merkelappp: String): Boolean {
        return merkelapper.contains(merkelappp)
    }
}

object ProdusentRegister {
    val json = this.javaClass.getResource("/produsent-register.json")!!
    val register: Map<String, List<String>> = objectMapper.readValue(json)
    fun finn(subject: String): Produsent {
        return Produsent(subject, register.getOrDefault(subject, emptyList()))
    }
}