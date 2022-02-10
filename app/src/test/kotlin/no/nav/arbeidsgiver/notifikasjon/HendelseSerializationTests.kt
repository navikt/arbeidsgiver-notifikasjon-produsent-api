package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime

/** Unit tests for historical and current formats that
 * may be seen in kafka log.
 */
class HendelseSerializationTests : DescribeSpec({

    describe("kun 'mottaker'") {
        val oppgaveOpprettet = Hendelse.OppgaveOpprettet(
            virksomhetsnummer = "",
            notifikasjonId = uuid("0"),
            hendelseId = uuid("0"),
            produsentId = "",
            kildeAppNavn = "",
            merkelapp = "",
            eksternId = "",
            mottakere = listOf(AltinnMottaker(
                serviceCode = "1",
                serviceEdition = "2",
                virksomhetsnummer = "3",
            )),
            tekst = "",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2000-01-01T01:01+01"),
            eksterneVarsler = listOf(),
        )


        it("mottaker parsed") {
            val json = laxObjectMapper.readTree(laxObjectMapper.writeValueAsString(oppgaveOpprettet))
            json.has("mottaker") shouldBe false
            json["mottakere"][0]["serviceCode"].asText() shouldBe "1"
            json["mottakere"][0]["serviceEdition"].asText() shouldBe "2"
            json["mottakere"][0]["virksomhetsnummer"].asText() shouldBe "3"
        }
    }
})