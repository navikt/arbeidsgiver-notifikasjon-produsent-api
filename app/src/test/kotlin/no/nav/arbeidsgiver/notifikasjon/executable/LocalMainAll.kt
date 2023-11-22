package no.nav.arbeidsgiver.notifikasjon.executable

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.executable.bruker_api.main as brukerApiMain
import no.nav.arbeidsgiver.notifikasjon.executable.dataprodukt.main as dataproduktMain
import no.nav.arbeidsgiver.notifikasjon.executable.ekstern_varsling.main as eksternVarslingMain
import no.nav.arbeidsgiver.notifikasjon.executable.kafka_reaper.main as kafkaReaperMain
import no.nav.arbeidsgiver.notifikasjon.executable.produsent_api.main as produsentApiMain
import no.nav.arbeidsgiver.notifikasjon.executable.replay_validator.main as replayValidatorMain
import no.nav.arbeidsgiver.notifikasjon.executable.skedulert_harddelete.main as skedulertHardDeleteMain
import no.nav.arbeidsgiver.notifikasjon.executable.skedulert_påminnelse.main as skedulertPåminnelseMain
import no.nav.arbeidsgiver.notifikasjon.executable.skedulert_utgått.main as skedulertUtgåttMain

/* start all */
fun main() = runBlocking {
    launch {
        brukerApiMain()
    }
    launch {
        kafkaReaperMain()
    }
    launch {
        produsentApiMain()
    }
    launch {
        eksternVarslingMain()
    }
    launch {
        replayValidatorMain()
    }
    launch {
        skedulertUtgåttMain()
    }
    launch {
        skedulertHardDeleteMain()
    }
    launch {
        skedulertPåminnelseMain()
    }

    launch {
        dataproduktMain()
    }
    Unit
}

