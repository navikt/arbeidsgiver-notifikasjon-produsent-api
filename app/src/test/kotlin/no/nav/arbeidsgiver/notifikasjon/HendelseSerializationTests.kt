package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

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
            val json = objectMapper.readTree(objectMapper.writeValueAsString(oppgaveOpprettet))
            json.has("mottaker") shouldBe false
            json["mottakere"][0]["serviceCode"].asText() shouldBe "1"
            json["mottakere"][0]["serviceEdition"].asText() shouldBe "2"
            json["mottakere"][0]["virksomhetsnummer"].asText() shouldBe "3"
        }
    }
})