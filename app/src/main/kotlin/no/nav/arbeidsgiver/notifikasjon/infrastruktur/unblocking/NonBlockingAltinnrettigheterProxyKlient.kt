package no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking

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
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> =
        blockingIO {
            blockingClient.hentOrganisasjoner(
                selvbetjeningToken,
                subject,
                serviceCode,
                serviceEdition,
                filtrerPåAktiveOrganisasjoner
            )
        }
}