package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.Period

class AutoSlettRepositoryFindToDeleteTests : DescribeSpec({

    describe("SkedulertHardDeleteRepository#hentDeSomSkalSlettes") {
        val database = testDatabase(SkedulertHardDelete.databaseConfig)
        val repository = SkedulertHardDeleteRepositoryImpl(database)
        val baseline = OffsetDateTime.parse("2020-01-01T01:01:01.00Z")

        repository.insert(id = "1", beregnetSlettetid = baseline + Duration.ofDays(2))
        repository.insert(id = "2", beregnetSlettetid = baseline - Duration.ofDays(2))
        repository.insert(id = "3", beregnetSlettetid = baseline - Duration.ofDays(2))
        repository.insert(id = "4", beregnetSlettetid = baseline + Duration.ofSeconds(2))
        repository.insert(id = "5", beregnetSlettetid = baseline - Duration.ofSeconds(2))
        repository.insert(id = "6", beregnetSlettetid = baseline)
        repository.insert(id = "7", beregnetSlettetid = baseline + Period.ofYears(3))
        repository.insert(id = "8", beregnetSlettetid = baseline - Period.ofYears(3))

        it("kun den som har passert er klar for slettes") {
            val skalSlettes = repository.hentSkedulerteHardDeletes(baseline.toInstant())
            val iderSomSkalSlettes = skalSlettes.map { it.aggregateId }
            iderSomSkalSlettes shouldContainExactlyInAnyOrder listOf("2", "3", "5", "6", "8").map { uuid(it) }
        }
    }
})

suspend fun SkedulertHardDeleteRepositoryImpl.insert(
    id: String,
    beregnetSlettetid: OffsetDateTime,
) {
    val uuid = uuid(id)
    this.oppdaterModellEtterHendelse(
        HendelseModel.SakOpprettet(
            hendelseId = uuid,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = uuid,
            grupperingsid = uuid.toString(),
            merkelapp = "",
            mottakere = listOf(HendelseModel.AltinnMottaker("", "", "")),
            tittel = "Sak $uuid",
            lenke = "https://dev.nav.no/$uuid",
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.parse("1234-12-19T23:32:32.01+05"),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.parse(
                beregnetSlettetid.toInstant().asOsloLocalDateTime().toString()
            ),
        ),
        kafkaTimestamp = Instant.EPOCH,
    )
}
