import { Organisasjon } from '@navikt/virksomhetsvelger';

export const MOCK_ORGANISASJONER: Organisasjon[] = [
  {
    orgnr: '811076112',
    navn: 'BALLSTAD OG HORTEN',
    underenheter: [
      { orgnr: '811076422', navn: 'BALLSTAD OG EIDSLANDET', underenheter: [] },
      { orgnr: '811076732', navn: 'BALLSTAD OG HAMARØY', underenheter: [] },
      { orgnr: '811076902', navn: 'BALLSTAD OG SÆTERVIK', underenheter: [] },
    ],
  },
  {
    orgnr: '810514442',
    navn: 'BAREKSTAD OG YTTERVÅG REGNSKAP',
    underenheter: [],
  },
  {
    orgnr: '910998250',
    navn: 'BIRI OG VANNAREID REVISJON',
    underenheter: [
      { orgnr: '910521551', navn: 'EIDSNES OG AUSTRE ÅMØY', underenheter: [] },
    ],
  },
  {
    orgnr: '910223208',
    navn: 'FRØNNINGEN OG LAUVSTAD REVISJON',
    underenheter: [],
  },
  {
    orgnr: '810989572',
    navn: 'HARSTAD OG TYSSEDAL REVISJON',
    underenheter: [],
  },
  {
    orgnr: '910646176',
    navn: 'HAVNNES OG ÅGSKARDET',
    underenheter: [],
  },
  {
    orgnr: '910175777',
    navn: 'KJØLLEFJORD OG ØKSFJORD',
    underenheter: [
      { orgnr: '910514318', navn: 'KYSTBASEN ÅGOTNES OG ILSENG REGNSKAP', underenheter: [] },
    ],
  },
  {
    orgnr: '910720120',
    navn: 'SKOTSELV OG HJELSET',
    underenheter: [
      { orgnr: '910793829', navn: 'SANDVÆR OG HOV', underenheter: [] },
    ],
  },
  {
    orgnr: '810771852',
    navn: 'STOL PÅ TORE',
    underenheter: [],
  },
  {
    orgnr: '910167200',
    navn: 'SØR-HIDLE OG STRAUMGJERDE OG SØNNER OG DØTRE',
    underenheter: [
      { orgnr: '910989626', navn: 'TROMVIK OG SPARBU REVISJON', underenheter: [] },
    ],
  },
  {
    orgnr: '910820834',
    navn: 'Tore sitt testselskap',
    underenheter: [
      { orgnr: '910521616', navn: 'UGGDAL OG STEINSDALEN', underenheter: [] },
      { orgnr: '810989602', navn: 'VALESTRANDSFOSSEN OG SØRLI OG SØNN REVISJON', underenheter: [] },
      { orgnr: '910989642', navn: 'VESTBY OG LOEN OG ALEKSANDERSEN REVISJON', underenheter: [] },
    ],
  },
];
