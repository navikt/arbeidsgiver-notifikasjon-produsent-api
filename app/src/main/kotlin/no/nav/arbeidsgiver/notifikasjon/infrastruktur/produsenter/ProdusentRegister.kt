package no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter

import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnReporteeMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnRolleMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AppName
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

object NærmesteLederDefinisjon : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is NærmesteLederMottaker -> true
            else -> false
        }
}

object AltinnReporteeDefinisjon : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is AltinnReporteeMottaker -> true
            else -> false
        }
}

data class AltinnRolleDefinisjon(val roleCode: String) : MottakerDefinisjon() {
    override fun akseptererMottaker(mottaker: Mottaker): Boolean =
        when (mottaker) {
            is AltinnRolleMottaker ->
                mottaker.roleDefinitionCode == roleCode
            else -> false
        }
}


object MottakerRegister {
    val servicecodeDefinisjoner: List<ServicecodeDefinisjon>
        get() {
            return MOTTAKER_REGISTER.filterIsInstance<ServicecodeDefinisjon>()
        }
    val rolleDefinisjoner: List<AltinnRolleDefinisjon>
        get() {
            return MOTTAKER_REGISTER.filterIsInstance<AltinnRolleDefinisjon>()
        }

    fun erDefinert(mottakerDefinisjon: MottakerDefinisjon): Boolean =
        when (mottakerDefinisjon) {
            is ServicecodeDefinisjon -> servicecodeDefinisjoner.contains(mottakerDefinisjon)
            is NærmesteLederDefinisjon -> true
            is AltinnReporteeDefinisjon -> true
            is AltinnRolleDefinisjon -> true
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

