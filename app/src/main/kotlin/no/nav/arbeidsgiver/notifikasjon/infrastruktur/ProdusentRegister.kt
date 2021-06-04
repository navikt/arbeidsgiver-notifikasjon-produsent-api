package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import java.util.*

typealias Merkelapp = String

data class Produsent(
    val id: String,
    val tillatteMerkelapper: List<Merkelapp> = emptyList(),
    val tillatteMottakere: List<MottakerDefinisjon> = emptyList()
) {

    fun kanSendeTil(merkelapp: Merkelapp): Boolean {
        return tillatteMerkelapper.contains(merkelapp)
    }

    fun kanSendeTil(mottaker: Mottaker): Boolean =
        tillatteMottakere.any { tillatMottaker ->
            tillatMottaker.akseptererMottaker(mottaker)
    }
}

sealed class MottakerDefinisjon {
    abstract fun akseptererMottaker(mottaker: Mottaker): Boolean
}

data class ServicecodeDefinisjon(
    val code: String,
    val version: String,
    val description: String? = null
) : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is AltinnMottaker ->
                mottaker.altinntjenesteKode == code && mottaker.altinntjenesteVersjon == version
            else -> false
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is ServicecodeDefinisjon
                && this.code == other.code
                && this.version == other.version
    }

    override fun hashCode(): Int = Objects.hash(code, version)
}

object NærmesteLederDefinisjon : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is NærmesteLederMottaker -> true
            else -> false
        }
}

object ProdusentRegister {
    private val REGISTER: Map<String, Produsent> = listOf(
        Produsent(
            id = "someproducer",
            tillatteMerkelapper = listOf(
                "tiltak",
                "sykemeldte",
                "rekruttering"
            ),
            tillatteMottakere = listOf(
                ServicecodeDefinisjon(code = "5216", version = "1"),
                ServicecodeDefinisjon(code = "5212", version = "1"),
                ServicecodeDefinisjon(code = "5384", version = "1"),
                ServicecodeDefinisjon(code = "5159", version = "1"),
                ServicecodeDefinisjon(code = "4936", version = "1"),
                ServicecodeDefinisjon(code = "5332", version = "2"),
                ServicecodeDefinisjon(code = "5332", version = "1"),
                ServicecodeDefinisjon(code = "5441", version = "1"),
                ServicecodeDefinisjon(code = "5516", version = "1"),
                ServicecodeDefinisjon(code = "5516", version = "2"),
                ServicecodeDefinisjon(code = "3403", version = "2"),
                ServicecodeDefinisjon(code = "5078", version = "1"),
                ServicecodeDefinisjon(code = "5278", version = "1"),
                NærmesteLederDefinisjon,
            )
        )
    ).associateBy { it.id }

    fun finn(subject: String): Produsent {
        val produsent = REGISTER.getOrDefault(subject, Produsent(subject))
        validate(produsent)
        return produsent
    }

    private fun validate(produsent: Produsent) {
        produsent.tillatteMottakere.forEach {
            when (it) {
                is ServicecodeDefinisjon -> check(MottakerRegister.erDefinert(it)) {
                    "Ugyldig mottaker $it for produsent $produsent"
                }
            }
        }
    }

    fun validateAll() = REGISTER.values.forEach(this::validate)
}

object MottakerRegister {
    private val REGISTER: List<MottakerDefinisjon> = listOf(
        ServicecodeDefinisjon(code = "5216", version = "1", description = "Mentortilskudd"),
        ServicecodeDefinisjon(code = "5212", version = "1", description = "Inkluderingstilskudd"),
        ServicecodeDefinisjon(code = "5384", version = "1", description = "Ekspertbistand"),
        ServicecodeDefinisjon(code = "5159", version = "1", description = "Lønnstilskudd"),
        ServicecodeDefinisjon(code = "4936", version = "1", description = "Inntektsmelding"),
        ServicecodeDefinisjon(code = "5332", version = "2", description = "Arbeidstrening"),
        ServicecodeDefinisjon(code = "5332", version = "1", description = "Arbeidstrening"),
        ServicecodeDefinisjon(code = "5441", version = "1", description = "Arbeidsforhold"),
        ServicecodeDefinisjon(code = "5516", version = "1", description = "Midlertidig lønnstilskudd"),
        ServicecodeDefinisjon(code = "5516", version = "2", description = "Varig lønnstilskudd'"),
        ServicecodeDefinisjon(code = "3403", version = "2", description = "Sykfraværsstatistikk"),
        ServicecodeDefinisjon(code = "5078", version = "1", description = "Rekruttering"),
        ServicecodeDefinisjon(code = "5278", version = "1", description = "Tilskuddsbrev om NAV-tiltak"),
    )

    val servicecodeDefinisjoner: List<ServicecodeDefinisjon>
        get() {
            return REGISTER.filterIsInstance<ServicecodeDefinisjon>()
        }

    fun erDefinert(mottakerDefinisjon: MottakerDefinisjon): Boolean =
        when (mottakerDefinisjon) {
            is ServicecodeDefinisjon -> servicecodeDefinisjoner.contains(mottakerDefinisjon)
            is NærmesteLederDefinisjon -> true
        }
    }
}
