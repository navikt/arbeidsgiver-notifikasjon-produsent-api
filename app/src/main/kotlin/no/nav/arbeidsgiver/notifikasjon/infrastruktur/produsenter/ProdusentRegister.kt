package no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRessursMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.AppName
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.util.*

typealias Merkelapp = String

data class Produsent(
    val id: String,
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

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is ServicecodeDefinisjon -> this.code == other.code && this.version == other.version
        else -> false
    }

    override fun hashCode(): Int = Objects.hash(code, version)
}

data class RessursIdDefinisjon(
    val ressursId: String,
) : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is AltinnRessursMottaker ->
                mottaker.ressursId == ressursId
            else -> false
        }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is RessursIdDefinisjon -> this.ressursId == other.ressursId
        else -> false
    }

    override fun hashCode(): Int = Objects.hash(ressursId)
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

    val ressursIdDefinisjoner: List<RessursIdDefinisjon>
        get() {
            return MOTTAKER_REGISTER.filterIsInstance<RessursIdDefinisjon>()
        }

    fun erDefinert(mottakerDefinisjon: MottakerDefinisjon): Boolean =
        when (mottakerDefinisjon) {
            is ServicecodeDefinisjon -> servicecodeDefinisjoner.contains(mottakerDefinisjon)
            is NærmesteLederDefinisjon -> true
            is RessursIdDefinisjon -> ressursIdDefinisjoner.contains(mottakerDefinisjon)
        }
}

interface ProdusentRegister {
    fun finn(appName: String): Produsent?
}

class ProdusentRegisterImpl(
    produsenter: List<Produsent>
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

    private val produsenterByName: Map<AppName, Produsent> =
        produsenter
            .flatMap { produsent ->
                produsent.accessPolicy.map { appNavn -> Pair(appNavn, produsent) }
            }
            .toMap()

    override fun finn(appName: AppName): Produsent? {
        return produsenterByName[appName] ?: run {
            log.error(
                """
                |Fant ikke produsent for appName=$appName.
                |Gyldige appName er: \n${produsenterByName.keys.joinToString(prefix = "| - ", postfix = "\n")}
            """.trimMargin()
            )
            return null
        }
    }
}

