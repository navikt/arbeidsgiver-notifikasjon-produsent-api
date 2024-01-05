package no.nav.arbeidsgiver.notifikasjon.replay_validator

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse

class ReplayValidatorServiceTest : DescribeSpec({

    describe("ReplayValidatorService opprett notifikasjon etter hard delete av sak") {
        val service = ReplayValidatorService(mutableListOf())

        val hendelser = listOf(
            EksempelHendelse.SakOpprettet.copy(
                merkelapp = "merkelapp",
                grupperingsid = "grupperingsid",
            ),
            EksempelHendelse.HardDelete.copy(
                merkelapp = "merkelapp",
                grupperingsid = "grupperingsid",
            ),
            EksempelHendelse.BeskjedOpprettet.copy(
                merkelapp = "merkelapp",
                grupperingsid = "grupperingsid",
            ),
        )

        hendelser.forEachIndexed { i, hendelse ->
            service.processHendelse(hendelse, PartitionHendelseMetadata(1, (i + 1).toLong()))
        }

        it("NotifikasjonCreateAfterHardDeleteSak skal finnes i repo") {
            val notifikasjonCreateAfterHardDeleteSak = service.repository.findNotifikasjonCreatesAfterHardDeleteSak()
            notifikasjonCreateAfterHardDeleteSak shouldContainExactly listOf(
                NotifikasjonCreateAfterHardDeleteSak(
                    id = hendelser.last().aggregateId.toString(),
                    produsentId = hendelser.last().produsentId!!,
                    merkelapp = "merkelapp",
                    grupperingsid = "grupperingsid",
                    createdOffset = "3",
                    createdPartition = "1",
                )
            )
        }
    }

    describe("ReplayValidatorService opprett notifikasjon før hard delete av sak") {
        val service = ReplayValidatorService(mutableListOf())

        val hendelser = listOf(
            EksempelHendelse.SakOpprettet.copy(
                merkelapp = "merkelapp",
                grupperingsid = "grupperingsid",
            ),
            EksempelHendelse.BeskjedOpprettet.copy(
                merkelapp = "merkelapp",
                grupperingsid = "grupperingsid",
            ),
            EksempelHendelse.HardDelete.copy(
                merkelapp = "merkelapp",
                grupperingsid = "grupperingsid",
            ),
        )

        hendelser.forEachIndexed { i, hendelse ->
            service.processHendelse(hendelse, PartitionHendelseMetadata(1, (i + 1).toLong()))
        }

        it("NotifikasjonCreateAfterHardDeleteSak skal finnes i repo") {
            val notifikasjonCreateAfterHardDeleteSak = service.repository.findNotifikasjonCreatesAfterHardDeleteSak()
            notifikasjonCreateAfterHardDeleteSak shouldBe emptyList()
        }
    }
})
