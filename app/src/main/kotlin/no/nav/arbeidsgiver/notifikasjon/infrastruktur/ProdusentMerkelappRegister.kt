package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import no.nav.arbeidsgiver.notifikasjon.objectMapper
import com.fasterxml.jackson.module.kotlin.*

data class ProdusentMerkelapp(val produsent: String, val merkelapper: List<String>) {
    fun har(merkelappp: String): Boolean {
        return merkelapper.contains(merkelappp)
    }
}

object ProdusentMerkelappRegister {
    val json = this.javaClass.getResource("/produsent-merkelapp-register.json")
    val register: Map<String, List<String>> = objectMapper.readValue(json)
    fun finn(produsent: String): ProdusentMerkelapp {
        return ProdusentMerkelapp(produsent, register.getOrDefault(produsent, emptyList()))
    }
}