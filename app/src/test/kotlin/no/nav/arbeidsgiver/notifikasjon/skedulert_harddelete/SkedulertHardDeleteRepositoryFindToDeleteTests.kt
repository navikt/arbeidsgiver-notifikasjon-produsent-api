package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.Period
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoSlettRepositoryFindToDeleteTest {

    @Test
    fun `SkedulertHardDeleteRepository#hentDeSomSkalSlettes`() =
        withTestDatabase(SkedulertHardDelete.databaseConfig) { database ->
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

            // kun den som har passert er klar for slettes
            val skalSlettes = repository.hentSkedulerteHardDeletes(baseline.toInstant())
            val iderSomSkalSlettes = skalSlettes.map { it.aggregateId }
            assertEquals(
                listOf("2", "3", "5", "6", "8").map { uuid(it) }.sorted(),
                iderSomSkalSlettes.sorted()
            )
        }
}

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
            nesteSteg = null,
            hardDelete = HendelseModel.LocalDateTimeOrDuration.parse(
                beregnetSlettetid.toInstant().asOsloLocalDateTime().toString()
            ),
            tilleggsinformasjon = null
        ),
        kafkaTimestamp = Instant.EPOCH,
    )
}
