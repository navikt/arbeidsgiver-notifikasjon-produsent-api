package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test

class EksternVarslingIdempotensTest {

    @Test
    fun `Ekstern Varlsing Idempotent oppførsel`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        EksempelHendelse.Alle.forEach { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse)
            repository.oppdaterModellEtterHendelse(hendelse)
        }
    }

    @Test
    fun `Håndterer partial replay hvor midt i hendelsesforløp etter harddelete`() {
        EksempelHendelse.Alle.forEach { hendelse ->
            withTestDatabase(EksternVarsling.databaseConfig) { database ->
                val repository = EksternVarslingRepository(database)

                repository.oppdaterModellEtterHendelse(
                    EksempelHendelse.HardDelete.copy(
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        aggregateId = hendelse.aggregateId,
                    )
                )
                repository.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
}
