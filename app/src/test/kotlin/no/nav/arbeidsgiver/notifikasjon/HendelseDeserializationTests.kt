package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.util.uuid

/** Unit tests for historical and current formats that
 * may be seen in kafka log.
 */
class HendelseDeserializationTests : DescribeSpec({

    describe("kun 'mottaker'") {
        val oppgaveOpprettet = laxObjectMapper.readValue<Hendelse>("""
            {
                "@type": "OppgaveOpprettet",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("0")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "merkelapp": "",
                "eksternId": "",
                "mottaker": {
                    "@type": "altinn",
                    "serviceCode": "1",
                    "serviceEdition": "2",
                    "virksomhetsnummer": "3"
                },
                "tekst": "",
                "lenke": "",
                "opprettetTidspunkt": "2020-01-01T01:01+01"
            }
        """)

        it("mottaker parsed") {
            oppgaveOpprettet as OppgaveOpprettet
            val mottaker = oppgaveOpprettet.mottakere.single() as AltinnMottaker
            mottaker.serviceCode shouldBe "1"
            mottaker.serviceEdition shouldBe "2"
            mottaker.virksomhetsnummer shouldBe "3"
        }
    }

    describe("ikke 'mottaker', kun 'mottakere'") {
        val oppgaveOpprettet = laxObjectMapper.readValue<Hendelse>("""
            {
                "@type": "OppgaveOpprettet",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("0")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "merkelapp": "",
                "eksternId": "",
                "mottakere": [
                    {
                        "@type": "altinn",
                        "serviceCode": "1",
                        "serviceEdition": "2",
                        "virksomhetsnummer": "3"
                    }
                ],
                "tekst": "",
                "lenke": "",
                "opprettetTidspunkt": "2020-01-01T01:01+01"
            }
        """)

        it("mottaker parsed") {
            oppgaveOpprettet as OppgaveOpprettet
            val mottaker = oppgaveOpprettet.mottakere.single() as AltinnMottaker
            mottaker.serviceCode shouldBe "1"
            mottaker.serviceEdition shouldBe "2"
            mottaker.virksomhetsnummer shouldBe "3"
        }
    }

    describe("leser 'notifikasjon', selv om den heter 'aggregateId' i kotlin") {
        val hardDelete = laxObjectMapper.readValue<Hendelse>("""
            {
                "@type": "HardDelete",
                "virksomhetsnummer": "0",
                "notifikasjonId": "${uuid("1")}",
                "hendelseId": "${uuid("0")}",
                "produsentId": "0",
                "kildeAppNavn": "",
                "deletedAt": "2020-01-01T01:01+01"
            }
        """)

        it("mottaker parsed") {
            hardDelete as HardDelete
            hardDelete.hendelseId shouldBe uuid("0")
            hardDelete.aggregateId shouldBe uuid("1")
        }
    }
})