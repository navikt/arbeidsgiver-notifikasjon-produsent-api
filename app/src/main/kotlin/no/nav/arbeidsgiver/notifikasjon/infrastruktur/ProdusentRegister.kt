package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
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
            is AltinnMottaker -> mottakere.filterIsInstance<ServicecodeDefinisjon>().any {
                mottaker.altinntjenesteKode == it.code
                        && mottaker.altinntjenesteVersjon == it.version
            }
            else -> true // TODO: fnr mottaker validering er ikke implementert enda
        }

    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class MottakerDefinisjon

@JsonTypeName("altinn")
data class ServicecodeDefinisjon(
    val code: String,
    val version: String,
    val description: String? = null
) : MottakerDefinisjon() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ServicecodeDefinisjon

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

object ProdusentRegister {
    private val REGISTER: Map<String, ProdusentDefinisjon> = listOf(
        ProdusentDefinisjon(
            id = "someproducer",
            merkelapper = listOf(
                "tiltak",
                "sykemeldte",
                "rekruttering"
            ),
            mottakere = listOf(
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
            )
        )
    ).associateBy { it.id }

    fun finn(subject: String): ProdusentDefinisjon {
        val produsent = REGISTER.getOrDefault(subject, ProdusentDefinisjon(subject))
        validate(produsent)
        return produsent
    }

    private fun validate(produsentDefinisjon: ProdusentDefinisjon) {
        produsentDefinisjon.mottakere.forEach {
            when (it) {
                is ServicecodeDefinisjon -> check(MottakerRegister.erDefinert(it)) {
                    "Ugyldig mottaker $it for produsent $produsentDefinisjon"
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

    fun erDefinert(servicecodeDefinisjon: ServicecodeDefinisjon): Boolean =
        servicecodeDefinisjoner.contains(servicecodeDefinisjon)
}
