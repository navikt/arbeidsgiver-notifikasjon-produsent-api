package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.altinn.services.common.fault._2009._10.AltinnFault
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.lang.RuntimeException
import java.time.Duration
import java.time.Instant

class AltinnResponseLogTest: DescribeSpec({
    val database = testDatabase(EksternVarsling.databaseConfig)
    val repository = EksternVarslingRepository(database)

    val varselId = uuid("1")

    val baseTime = Instant.parse("2020-01-01T01:01:01Z")
    val deleteBack = Duration.ofDays(15)

    describe("altinn response log, og dens sletting ") {
        it("Kan lagre feil-responser fra altinn") {
            val altinnFault = AltinnFault()
            repository.recordAltinnVarselKlientError(
                varselId = varselId,
                altinnVarselKlientResponse = AltinnVarselKlientResponse.Feil(
                    rå = laxObjectMapper.valueToTree(altinnFault),
                    altinnFault = altinnFault,
                ),
                now = baseTime,
            )
        }

        it("slettejobb kort tid etter har ingen effekt") {
            repository.deleteOldResponseLog(
                maxAge = deleteBack,
                now = baseTime + Duration.ofHours(1),
            )
            database.antallInnslag() shouldBe 1
        }

        it("kan lagre exceptions fra altinn") {
            repository.recordAltinnVarselKlientError(
                varselId = varselId,
                altinnVarselKlientResponse = UkjentException(
                    RuntimeException("Her har det skjedd en feil")
                ),
                now = baseTime + Duration.ofDays(10),
            )
        }

        it("ser to lagrede innslag") {
            database.antallInnslag() shouldBe 2
        }

        it("Kan slette gamle logginnslag") {
            repository.deleteOldResponseLog(
                maxAge = deleteBack,
                now = baseTime + Duration.ofDays(10) + Duration.ofDays(10),
            )
            database.antallInnslag() shouldBe 1
        }
    }
})

private suspend fun Database.antallInnslag() =
    nonTransactionalExecuteQuery("""
               select * from altinn_response_log
            """) {
    }
        .size
