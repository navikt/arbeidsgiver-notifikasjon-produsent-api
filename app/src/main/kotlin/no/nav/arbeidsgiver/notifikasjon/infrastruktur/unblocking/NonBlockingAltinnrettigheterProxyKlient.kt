package no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*

/*
@JvmInline
value */class NonBlockingAltinnrettigheterProxyKlient(
    private val blockingClient: AltinnrettigheterProxyKlient
) {
    suspend fun hentOrganisasjoner(
        selvbetjeningToken: SelvbetjeningToken,
        subject: Subject,
        serviceCode: ServiceCode,
        serviceEdition: ServiceEdition,
        filtrerP√•AktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> =
        withContext(Dispatchers.IO) {
            blockingClient.hentOrganisasjoner(
                selvbetjeningToken,
                subject,
                serviceCode,
                serviceEdition,
                filtrerP√•AktiveOrganisasjoner
            )
        }
}