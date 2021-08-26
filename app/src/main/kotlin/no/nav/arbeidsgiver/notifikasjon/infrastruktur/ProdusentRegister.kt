package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import java.util.*

typealias Merkelapp = String
typealias AppName = String
typealias ClientId = String


data class Produsent(
    val accessPolicy: List<AppName>,
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
                mottaker.serviceCode == code && mottaker.serviceEdition == version
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

object PreAuthorizedApps {
    data class Elem(
        val name: AppName,
        val clientId: ClientId
    )

    val json = System.getenv("AZURE_APP_PRE_AUTHORIZED_APPS")!!
    val map = objectMapper
        .readValue<List<Elem>>(json)
}

interface ProdusentRegister {
    fun finn(subject: String): Produsent
    fun validateAll()
}

object ProdusentRegisterImpl : ProdusentRegister {
    private val REGISTER: List<Produsent> = listOf(
        Produsent(
            accessPolicy = listOf(
                "dev-gcp:fager:test-produsent"
            ),
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
    )


    private val clientIdToProdusent: Map<ClientId, Produsent> = PreAuthorizedApps.map
        .flatMap { elem ->
            val produsent = REGISTER.find {
                it.accessPolicy.contains(elem.name)
            } ?: return@flatMap listOf()
            listOf(Pair(elem.clientId, produsent))
        }
        .toMap()

    private val noopProdusent = Produsent(
            accessPolicy = listOf()
        )

    override fun finn(subject: ClientId): Produsent {
        return clientIdToProdusent.getOrDefault(subject, noopProdusent)
    }

    override fun validateAll() = REGISTER.forEach(this::validate)

    private fun validate(produsent: Produsent) {
        produsent.tillatteMottakere.forEach {
            check(MottakerRegister.erDefinert(it)) {
                "Ugyldig mottaker $it for produsent $produsent"
            }
        }
    }
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
