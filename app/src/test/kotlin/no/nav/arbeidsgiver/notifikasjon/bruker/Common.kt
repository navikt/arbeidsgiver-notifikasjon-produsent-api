package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import java.time.Instant

suspend fun BrukerRepository.oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse){
    oppdaterModellEtterHendelse(hendelse, HendelseModel.HendelseMetadata(Instant.now()))
}