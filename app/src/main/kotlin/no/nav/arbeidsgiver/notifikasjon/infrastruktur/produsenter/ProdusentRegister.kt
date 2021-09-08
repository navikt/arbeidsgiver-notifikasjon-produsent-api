package no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
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

object MottakerRegister {
    val servicecodeDefinisjoner: List<ServicecodeDefinisjon>
        get() {
            return MOTTAKER_REGISTER.filterIsInstance<ServicecodeDefinisjon>()
        }

    fun erDefinert(mottakerDefinisjon: MottakerDefinisjon): Boolean =
        when (mottakerDefinisjon) {
            is ServicecodeDefinisjon -> servicecodeDefinisjoner.contains(mottakerDefinisjon)
            is NærmesteLederDefinisjon -> true
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
    fun finn(produsentid: String): Produsent?
}

class ProdusentRegisterImpl(
    private val produsenter: List<Produsent>
) : ProdusentRegister {

    val log = logger()

    init {
        produsenter.forEach { produsent ->
            produsent.tillatteMottakere.forEach {
                check(MottakerRegister.erDefinert(it)) {
                    "Ugyldig mottaker $it for produsent $produsent"
                }
            }
        }
    }

    private val clientIdToProdusent: Map<ClientId, Produsent> = PreAuthorizedApps.map
        .flatMap { elem ->
            val produsent = produsenter.find {
                it.accessPolicy.contains(elem.name)
            } ?: return@flatMap listOf()
            listOf(Pair(elem.clientId, produsent))
        }
        .toMap()

    private val noopProdusent = Produsent(
        accessPolicy = listOf()
    )

    override fun finn(produsentid: ClientId): Produsent {
        return clientIdToProdusent.getOrElse(produsentid) {
            log.warn("fant ikke produsent for clientId=$produsentid. produsenter=$clientIdToProdusent")
            noopProdusent
        }
    }

}

