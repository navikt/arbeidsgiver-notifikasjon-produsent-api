package no.nav.arbeidsgiver.notifikasjon.executable

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.executable.bruker_api.main as brukerApiMain
import no.nav.arbeidsgiver.notifikasjon.executable.kafka_reaper.main as kafkaReaperMain
import no.nav.arbeidsgiver.notifikasjon.executable.produsent_api.main as produsentApiMain
import no.nav.arbeidsgiver.notifikasjon.executable.statistikk.main as statistikkMain

/* start all */
fun main(args: Array<String>) = runBlocking {
    launch {
        brukerApiMain(args)
    }
    launch {
        kafkaReaperMain(args)
    }
    launch {
        produsentApiMain(args)
    }
    launch {
        statistikkMain(args)
    }
    Unit
}

