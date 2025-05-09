import { AnalyticsEvent } from '@navikt/nav-dekoratoren-moduler';

type PanelEkspander = AnalyticsEvent<'panel-ekspander', {
  tittel: string;
  url: string;
  'antall-notifikasjoner': number;
  'antall-ulestenotifikasjoner': number;
  'antall-lestenotifikasjoner': number;
}>

type PanelKollaps = AnalyticsEvent<'panel-kollaps', {
  url: string;
  tittel: string;
}>;

type NotifikasjonKlikk = AnalyticsEvent<
  'notifikasjon klikk',
  {
    url: string;
    index: number;
    'merkelapp': string;
    'klikket-paa-tidligere': boolean;
    'destinasjon': string;
  }
>;

type LastKomponent = AnalyticsEvent<'last-komponent', {
  tittel: string;
  url: string;
  'antall-notifikasjoner': number;
  'antall-ulestenotifikasjoner': number;
  'antall-lestenotifikasjoner': number;
}>;

type PiltastNavigasjon = AnalyticsEvent<'piltast-navigasjon', {
  url: string;
}>

export type CustomEvents = NotifikasjonKlikk | PanelEkspander | PanelKollaps | LastKomponent | PiltastNavigasjon;
