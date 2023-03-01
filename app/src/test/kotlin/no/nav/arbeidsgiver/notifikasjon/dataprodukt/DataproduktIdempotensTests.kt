package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.NoopHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant

class DataproduktIdempotensTests : DescribeSpec({
    val database = testDatabase(Dataprodukt.databaseConfig)
    val service = DataproduktModel(database)

    val now = Instant.now()

    describe("Dataprodukt Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            if (hendelse is HendelseModel.BrukerKlikket) {
                service.oppdaterModellEtterHendelse(
                    EksempelHendelse.BeskjedOpprettet.copy(hendelseId = hendelse.notifikasjonId, notifikasjonId = hendelse.notifikasjonId),
                    HendelseMetadata(now)
                )
            }

            if (hendelse is HendelseModel.NyStatusSak) {
                service.oppdaterModellEtterHendelse(
                    EksempelHendelse.SakOpprettet.copy(sakId = hendelse.sakId),
                    HendelseMetadata(now)
                )
            }

            service.oppdaterModellEtterHendelse(hendelse, HendelseMetadata(now))
            service.oppdaterModellEtterHendelse(hendelse, HendelseMetadata(now))
        }
    }
})
