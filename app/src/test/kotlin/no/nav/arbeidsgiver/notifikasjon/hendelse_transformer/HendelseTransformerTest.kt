package no.nav.arbeidsgiver.notifikasjon.hendelse_transformer

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HendelseTransformerTest {
    @Test
    fun `Transform hendelse Duration as seconds`() {
        val inputWithError = laxObjectMapper.readTree(inputWithErrorJson)
        val transformed = fiksNumberTilDurationStringISkedulertHardDelete(inputWithError)
        // should transfrom numbers to duration of seconds
        assertNotNull(transformed)
        transformed as SakOpprettet
        assertNotNull(transformed.hardDelete)
        assertEquals(ISO8601Period.parse("PT63072000S"), transformed.hardDelete!!.omOrNull())

        val jsonNode2 = laxObjectMapper.readTree(inputOkJson)
        // no transformation needed
        assertEquals(null, fiksNumberTilDurationStringISkedulertHardDelete(jsonNode2))
    }
}

//language=JSON
private const val inputWithErrorJson = """
    {
  "@type": "SakOpprettet",
  "hendelseId": "3c0eef3b-97ef-42fd-812b-81ce0cda212c",
  "virksomhetsnummer": "910825526",
  "produsentId": "permitteringsmelding-notifikasjon",
  "kildeAppNavn": "dev-gcp:permittering-og-nedbemanning:permitteringsmelding-notifikasjon",
  "sakId": "3c0eef3b-97ef-42fd-812b-81ce0cda212c",
  "grupperingsid": "f9ba97d4-493a-46e3-a63e-5c4a7b44ae7c",
  "merkelapp": "Innskrenking av arbeidstid",
  "mottakere": [
    {
      "@type": "altinn",
      "serviceCode": "4936",
      "serviceEdition": "1",
      "virksomhetsnummer": "910825526"
    },
    {
      "@type": "altinn",
      "serviceCode": "5516",
      "serviceEdition": "1",
      "virksomhetsnummer": "910825526"
    },
    {
      "@type": "naermesteLeder",
      "naermesteLederFnr": "123",
      "ansattFnr": "321",
      "virksomhetsnummer": "910825526"
    }
  ],
  "tittel": "Melding om innskrenking av arbeidstid",
  "lenke": "https://permitteringsskjema.dev.nav.no/permittering/skjema/kvitteringsside/f9ba97d4-493a-46e3-a63e-5c4a7b44ae7c",
  "oppgittTidspunkt": "2022-04-29T12:19:41.374333338Z",
  "mottattTidspunkt": "2022-04-29T14:19:41.810478557+02:00",
  "hardDelete": {
    "@type": "Duration",
    "value": 63072000.000000000
  }
}
    """

//language=JSON
private const val inputOkJson = """
    {
  "@type": "SakOpprettet",
  "hendelseId": "3c0eef3b-97ef-42fd-812b-81ce0cda212c",
  "virksomhetsnummer": "910825526",
  "produsentId": "permitteringsmelding-notifikasjon",
  "kildeAppNavn": "dev-gcp:permittering-og-nedbemanning:permitteringsmelding-notifikasjon",
  "sakId": "3c0eef3b-97ef-42fd-812b-81ce0cda212c",
  "grupperingsid": "f9ba97d4-493a-46e3-a63e-5c4a7b44ae7c",
  "merkelapp": "Innskrenking av arbeidstid",
  "mottakere": [
    {
      "@type": "altinn",
      "serviceCode": "4936",
      "serviceEdition": "1",
      "virksomhetsnummer": "910825526"
    },
    {
      "@type": "altinn",
      "serviceCode": "5516",
      "serviceEdition": "1",
      "virksomhetsnummer": "910825526"
    },
    {
      "@type": "naermesteLeder",
      "naermesteLederFnr": "123",
      "ansattFnr": "321",
      "virksomhetsnummer": "910825526"
    }
  ],
  "tittel": "Melding om innskrenking av arbeidstid",
  "lenke": "https://permitteringsskjema.dev.nav.no/permittering/skjema/kvitteringsside/f9ba97d4-493a-46e3-a63e-5c4a7b44ae7c",
  "oppgittTidspunkt": "2022-04-29T12:19:41.374333338Z",
  "mottattTidspunkt": "2022-04-29T14:19:41.810478557+02:00",
  "hardDelete": {
    "@type": "Duration",
    "value": "P2Y"
  }
}
"""