package no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

typealias AppName = String
typealias ClientId = String

interface AzurePreAuthorizedApps {
    fun lookup(clientId: ClientId): AppName?
}

class AzurePreAuthorizedAppsImpl : AzurePreAuthorizedApps {
    private val log = logger()

    data class Elem(
        val name: AppName,
        val clientId: ClientId
    )

    private val authorizedApps = laxObjectMapper.readValue<List<Elem>>(System.getenv("AZURE_APP_PRE_AUTHORIZED_APPS")!!)

    private val lookupTable: Map<ClientId, AppName> =
        authorizedApps.associate { Pair(it.clientId, it.name) }

    init {
        val prettyString = authorizedApps.joinToString(separator = ";\n") {
            "name: ${it.name} clientId: ${it.clientId}"
        }
        log.info("PreAuthorizedApps: \n{}", prettyString)

        if (authorizedApps.size != lookupTable.size) {
            log.warn("duplicate clientId detected")
        }
    }

    override fun lookup(clientId: ClientId): AppName? = lookupTable[clientId]
}