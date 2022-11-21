package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.BeforeContainerListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.logging.TestLogger
import junit.framework.TestListener
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import java.util.*

fun TestConfiguration.fakeHendelseProdusent(): FakeHendelseProdusent {
    return FakeHendelseProdusent().also {
        register(object: BeforeContainerListener {
            override suspend fun beforeContainer(testCase: TestCase) {
                it.hendelser.clear()
            }
        })
    }

}
class FakeHendelseProdusent: HendelseProdusent {
    val hendelser = mutableListOf<HendelseModel.Hendelse>()

    inline fun <reified T> hendelserOfType() = hendelser.filterIsInstance<T>()

    override suspend fun send(hendelse: HendelseModel.Hendelse) {
        hendelser.add(hendelse)
    }

    override suspend fun tombstone(key: UUID, orgnr: String) {
        hendelser.removeIf {
            it.hendelseId == key
        }
    }

    fun clear() {
        hendelser.clear()
    }
}