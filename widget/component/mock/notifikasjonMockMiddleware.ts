import fs from 'fs';
import { ApolloServer, gql, Config } from 'apollo-server-express';
import casual from 'casual';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { Express } from 'express';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const schemaPath = join(__dirname, './bruker.graphql');

const roundDate = (millis: number): Date => {
  const date = new Date();
  return new Date(Math.floor(date.getTime() / millis) * millis);
};

const utgattDate = (): Date => {
  const start = new Date(2023, 1, 5);
  const end = new Date();
  const date = new Date(+start + Math.random() * (end.getTime() - start.getTime()));
  const hour = start.getHours() + (Math.random() * (end.getHours() - start.getHours())) | 0;
  date.setHours(hour);
  return date;
};

const datePlusTimer = (date: Date, hours: number): Date => {
  return new Date(date.getTime() + hours * 60 * 60 * 1000);
};

const casualDate = (): Date => {
  const date = new Date();
  if (casual.integer(0, 1)) date.setHours(date.getHours() - casual.integer(0, 60));
  if (casual.integer(0, 1)) date.setMinutes(date.getMinutes() - casual.integer(0, 60));
  if (casual.integer(0, 5)) date.setDate(date.getDate() - casual.integer(0, 31));
  if (casual.integer(0, 10) === 0) date.setMonth(date.getMonth() - casual.integer(0, 12));
  if (casual.integer(0, 49) === 0) date.setFullYear(date.getFullYear() - casual.integer(0, 1));
  return date;
};

const casualFutureDate = (): Date => {
  const date = new Date();
  if (casual.integer(0, 1)) date.setHours(date.getHours() + casual.integer(0, 60));
  if (casual.integer(0, 1)) date.setMinutes(date.getMinutes() + casual.integer(0, 60));
  if (casual.integer(0, 5)) date.setDate(date.getDate() + casual.integer(0, 31));
  if (casual.integer(0, 10) === 0) date.setMonth(date.getMonth() + casual.integer(0, 12));
  if (casual.integer(0, 49) === 0) date.setFullYear(date.getFullYear() + casual.integer(0, 1));
  return date;
};

const eksempler: Record<string, string[]> = {
  Inntektsmelding: ['Inntektsmelding mottatt', 'Send inn inntektsmelding for sykepenger'],
  Permittering: ['Varsel om permittering sendt', 'Permitteringsmelding sendt', 'Søknad om lønnskompensasjon ved permittering sendt'],
  Masseoppsigelse: ['Varsel om masseoppsigelse sendt', 'Masseoppsigelse sendt', 'Søknad om lønnskompensasjon ved masseoppsigelse sendt'],
  'Innskrenkning i arbeidstiden': ['Varsel om innskrenkning i arbeidstiden sendt', 'Innskrenkningsmelding sendt', 'Søknad om lønnskompensasjon ved innskrenkning i arbeidstiden sendt'],
  Yrkesskade: ['Yrkesskademelding sendt', 'Søknad om yrkesskadeerstatning sendt'],
  //TODO: Fylle inn mock-eksempler
  Foreldrepenger: []
};

const saker: string[] = [
  'Varsel om permittering 24 ansatte TEST',
  'Søknad om fritak fra arbeidsgiverperioden – gravid ansatt Glovarm Bagasje',
  'Søknad om fritak fra arbeidsgiverperioden – kronisk sykdom Akrobatisk Admiral'
];

type NotifikasjonType = 'Oppgave' | 'Beskjed' | 'Kalenderavtale';

const TidslinjeElement = (type: string): any => {
  const merkelapp = casual.random_key(eksempler);
  const tekst = casual.random_element(eksempler[merkelapp]);
  const erUtgåttOppgave = type === 'Oppgave' && casual.boolean;
  const tilstand = erUtgåttOppgave ? 'UTGAATT' : casual.random_element(['NY', 'UTFOERT']);
  const paaminnelseTidspunkt = casual.boolean ? casualDate().toISOString() : null;
  const opprettetTidspunkt = casualDate().toISOString();
  const startTidspunkt = casual.boolean ? utgattDate().toISOString() : casualFutureDate().toISOString();
  const sluttTidspunkt = casual.boolean ? datePlusTimer(new Date(startTidspunkt), 1).toISOString() : null;

  return {
    __typename: type,
    id: Math.random().toString(36),
    tekst,
    ...(type === 'BeskjedTidslinjeElement' ? { opprettetTidspunkt } : {}),
    ...(type === 'OppgaveTidslinjeElement' ? {
      tilstand,
      paaminnelseTidspunkt,
      utgaattTidspunkt: erUtgåttOppgave ? utgattDate().toISOString() : null,
      utfoertTidspunkt: tilstand === 'UTFOERT' ? utgattDate().toISOString() : null,
      frist: casual.boolean ? casualDate().toISOString() : null,
      opprettetTidspunkt
    } : {}),
    ...(type === 'KalenderavtaleTidslinjeElement' ? {
      tekst: 'Dialogmøte ' + casual.random_element(['Mikke', 'Minni', 'Dolly', 'Donald', 'Langbein']),
      startTidspunkt,
      sluttTidspunkt,
      lokasjon: casual.boolean ? null : {
        adresse: 'Thorvald Meyers gate 2B',
        postnummer: '0473',
        poststed: 'Oslo'
      },
      digitalt: casual.boolean,
      avtaletilstand: casual.random_element([
        'VENTER_SVAR_FRA_ARBEIDSGIVER',
        'ARBEIDSGIVER_HAR_GODTATT',
        'ARBEIDSGIVER_VIL_AVLYSE',
        'ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED',
        'AVLYST'
      ])
    } : {})
  };
};

const Notifikasjon = (type: NotifikasjonType): any => {
  const merkelapp = casual.random_key(eksempler);
  const tekst = casual.random_element(eksempler[merkelapp]);
  const erUtgåttOppgave = type === 'Oppgave' && casual.boolean;
  const tilstand = type === 'Oppgave' ? { tilstand: erUtgåttOppgave ? 'UTGAATT' : casual.random_element(['NY', 'UTFOERT']) } : {};
  const opprettetTidspunkt = casualDate().toISOString();
  const paaminnelseTidspunkt = casual.boolean ? casualDate().toISOString() : null;
  const startTidspunkt = casual.boolean ? utgattDate().toISOString() : casualFutureDate().toISOString();
  const sluttTidspunkt = casual.boolean ? datePlusTimer(new Date(startTidspunkt), 1).toISOString() : null;
  const tilleggsinformasjoner = ["Sykemeldingsperiode: 01.09.2024 - 30.09.2024", null, "Du må sende inntektsmelding"];

  return {
    __typename: type,
    id: Math.random().toString(36),
    merkelapp,
    tekst,
    lenke: `#${casual.word}`,
    opprettetTidspunkt,
    ...(type === 'Oppgave' ? {
      utgaattTidspunkt: erUtgåttOppgave ? casualDate().toISOString() : null,
      paaminnelseTidspunkt,
      frist: casual.boolean ? casualDate().toISOString() : null
    } : {}),
    ...(type === 'Kalenderavtale' ? {
      tekst: 'Dialogmøte Dolly',
      startTidspunkt,
      sluttTidspunkt,
      lokasjon: {
        adresse: 'Thorvald Meyers gate 2B',
        postnummer: '0473',
        poststed: 'Oslo'
      },
      digitalt: casual.boolean,
      avtaletilstand: casual.random_element([
        'VENTER_SVAR_FRA_ARBEIDSGIVER',
        'ARBEIDSGIVER_HAR_GODTATT',
        'ARBEIDSGIVER_VIL_AVLYSE',
        'ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED',
        'AVLYST'
      ])
    } : {}),
    sorteringTidspunkt: paaminnelseTidspunkt !== null ? paaminnelseTidspunkt : opprettetTidspunkt,
    ...tilstand,
    virksomhet: {
      navn: casual.random_element([
        'Ballstad og Hamarøy',
        'Saltrød og Høneby',
        'Arendal og Bønes Revisjon',
        'Gravdal og Solli Revisjon',
        'Storfonsa og Fredrikstad Regnskap'
      ])
    },
    sak: {
      tittel: casual.random_element(saker),
      tilleggsinformasjon: casual.random_element(tilleggsinformasjoner)
    }
  };
};

const mocks = (): Record<string, any> => ({
  Query: () => ({
    notifikasjoner: () => ({
      notifikasjoner: Array.from({ length: 10 }, () => Notifikasjon(casual.random_element(['Oppgave', 'Beskjed', 'Kalenderavtale']))).sort((a, b) => b.sorteringTidspunkt.localeCompare(a.sorteringTidspunkt)),
      feilAltinn: false,
      feilDigiSyfo: false
    }),
    saker: () => ({
      saker: Array.from({ length: 30 }, () => {
        const tittel = casual.random_element(saker);
        return {
          tittel,
          lenke: '#',
          virksomhet: { navn: 'Gamle Fredikstad og Riksdalen regnskap' },
          tidslinje: Array.from({ length: casual.integer(0, 3) }, () => TidslinjeElement(casual.random_element(['OppgaveTidslinjeElement', 'BeskjedTidslinjeElement', 'KalenderavtaleTidslinjeElement']))),
          sisteStatus: {
            tekst: casual.random_element(['Mottatt', 'Under behandling', 'Utbetalt']),
            tidspunkt: casualDate().toISOString()
          },
          nesteSteg: casual.random_element(["Saksbehandlingstiden er lang. Du kan forvente refusjon utbetalt i januar 2025.", "Denne saken vil bli behandlet innen 1. juli.", "Denne saken blir nok ikke behandlet.", ...new Array(7).fill(null)]),
          frister: casual.boolean ? [
            casual.random_element([null, casualDate().toISOString().slice(0, 10)]),
            casual.random_element([null, new Date().toISOString().replace(/T.*/, '')])
          ] : []
        };
      }),
      totaltAntallSaker: 314,
      sakstyper: Object.keys(eksempler).map(navn => ({ navn, antall: casual.integer(0, 10) }))
    }),
    sakstyper: Object.keys(eksempler).map(navn => ({ navn })),
    notifikasjonerSistLest: {
      __typename: 'NotifikasjonerSistLest',
      tidspunkt: utgattDate().toISOString()
    }
  }),
  Int: () => casual.integer(0, 1000),
  String: () => casual.string,
  ISO8601DateTime: () => roundDate(5000).toISOString(),
  ISO8601Date: () => roundDate(5000).toISOString().slice(0, 10),
  Virksomhet: () => ({ navn: casual.catch_phrase })
});

export const createApolloServer = (options: Partial<Config> = {}): ApolloServer => {
  const { mocks: apolloServerOptionsMocks, ...apolloServerOptions } = options;
  const mocksObject = typeof apolloServerOptionsMocks === 'object' && apolloServerOptionsMocks !== null
    ? apolloServerOptionsMocks
    : {};

  const data = fs.readFileSync(schemaPath);

  return new ApolloServer({
    typeDefs: gql(data.toString()),
    mocks: { ...mocks(), ...mocksObject },
    ...apolloServerOptions,
  });
};

export const applyNotifikasjonMockMiddleware = (middlewareOptions: { app: Express, path?: string }, apolloServerOptions?: Partial<Config>): void => {
  const apolloServer = createApolloServer(apolloServerOptions);
  apolloServer.start()
    .then(() => {
      apolloServer.applyMiddleware(middlewareOptions);
    })
    .catch(error => {
      console.error('Error starting Apollo Server:', error);
    });
};
