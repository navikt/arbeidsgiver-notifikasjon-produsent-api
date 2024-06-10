const fs = require('fs')
const path = require('path')
let casual

const roundDate = (millis) => {
  const date = new Date()
  return new Date(Math.floor(date.getTime() / millis) * millis)
}

const utgåttDate = () => {
  const start = new Date(2023, 1, 5)
  const end = new Date()
  var date = new Date(+start + Math.random() * (end - start))
  var hour = start.getHours() + Math.random() * (end.getHours() - start.getHours()) | 0
  date.setHours(hour)
  return date
}

const datePlusTimer = (date, hours) => {
  return new Date(date.getTime() + hours * 60 * 60 * 1000)
}

const casualDate = () => {
  const date = new Date()

  if (casual.integer(0, 1)) {
    date.setHours(date.getHours() - casual.integer(0, 60))
  }

  if (casual.integer(0, 1)) {
    date.setMinutes(date.getMinutes() - casual.integer(0, 60))
  }

  if (casual.integer(0, 5)) {
    date.setDate(date.getDate() - casual.integer(0, 31))
  }

  if (casual.integer(0, 10) === 0) {
    date.setMonth(date.getMonth() - casual.integer(0, 12))
  }

  if (casual.integer(0, 49) === 0) {
    date.setFullYear(date.getFullYear() - casual.integer(0, 1))
  }
  return date
}

const casualFutureDate = () => {
  const date = new Date()

  if (casual.integer(0, 1)) {
    date.setHours(date.getHours() + casual.integer(0, 60))
  }

  if (casual.integer(0, 1)) {
    date.setMinutes(date.getMinutes() + casual.integer(0, 60))
  }

  if (casual.integer(0, 5)) {
    date.setDate(date.getDate() + casual.integer(0, 31))
  }

  if (casual.integer(0, 10) === 0) {
    date.setMonth(date.getMonth() + casual.integer(0, 12))
  }

  if (casual.integer(0, 49) === 0) {
    date.setFullYear(date.getFullYear() + casual.integer(0, 1))
  }
  return date
}

const eksempler = {
  'Inntektsmelding': [
    'Inntektsmelding mottatt',
    'Send inn inntektsmelding for sykepenger'
    ],
  'Permittering': [
    'Varsel om permittering sendt',
    'Permitteringsmelding sendt',
    'Søknad om lønnskompensasjon ved permittering sendt',
    ],
  'Masseoppsigelse': [
    'Varsel om masseoppsigelse sendt',
    'Masseoppsigelse sendt',
    'Søknad om lønnskompensasjon ved masseoppsigelse sendt',
    ],
  'Innskrenkning i arbeidstiden': [
    'Varsel om innskrenkning i arbeidstiden sendt',
    'Innskrenkningsmelding sendt',
    'Søknad om lønnskompensasjon ved innskrenkning i arbeidstiden sendt',
    ],
  'Yrkesskade': [
    'Yrkesskademelding sendt',
    'Søknad om yrkesskadeerstatning sendt',
    ],
  'Lønnstilskudd': [
    'Ny avtale om arbeidstiltak opprettet. Åpne avtale og fyll ut innholdet.',
    'Avtale om arbeidstiltak godkjent.',
    'Du kan nå søke om refusjon.',
    'Mål i avtale endret av veileder.',
    'Oppfølging og tilrettelegging i avtale endret av veileder.',
    'Avtale forkortet.',
    'Avtale forlenget av veileder.',
    'Tilskuddsberegning i avtale endret av veileder.',
    'Avtalen må godkjennes på nytt.',
    'Kontaktinformasjon i avtale endret av veileder.'
  ],
  'Mentor': [
    'Du kan nå søke om refusjon.',
    'Mål i avtale endret av veileder.',
    'Oppfølging og tilrettelegging i avtale endret av veileder.',
    'Avtale forkortet.',
    'Avtale forlenget av veileder.',
    'Avtalen må godkjennes på nytt.',
    'Kontaktinformasjon i avtale endret av veileder.'
  ],
  'Sommerjobb': [
    'Stillingsbeskrivelse i avtale endret av veileder.',
    'Mål i avtale endret av veileder.',
    'Avtale forkortet.',
    'Avtale forlenget av veileder.',
    'Tilskuddsberegning i avtale endret av veileder.',
    'Avtalen må godkjennes på nytt.',
    'Kontaktinformasjon i avtale endret av veileder.'
  ],
  'Arbeidstrening': [
    'Ny avtale om arbeidstiltak opprettet. Åpne avtale og fyll ut innholdet.',
    'Avtale om arbeidstiltak godkjent.',
    'Du kan nå søke om refusjon.',
    'Stillingsbeskrivelse i avtale endret av veileder.',
    'Mål i avtale endret av veileder.',
    'Oppfølging og tilrettelegging i avtale endret av veileder.',
    'Avtale forkortet.',
    'Avtale forlenget av veileder.',
    'Tilskuddsberegning i avtale endret av veileder.',
    'Avtalen må godkjennes på nytt.',
    'Kontaktinformasjon i avtale endret av veileder.'
  ]
}

const saker = [
  'Varsel om permittering 24 ansatte TEST',
  'Søknad om fritak fra arbeidsgiverperioden – gravid ansatt Glovarm Bagasje',
  'Søknad om fritak fra arbeidsgiverperioden – kronisk sykdom Akrobatisk Admiral',
  'Permitteringsmelding 14 ansatte TEST',
  'Lønnskompensasjon ved permittering TEST',
  'Varsel om permittering 12 ansatte TEST',
  'Søknad om fritak fra arbeidsgiverperioden – kronisk sykdom Gylden Karneval\n',
  'Refusjon - fritak fra arbeidsgiverperioden - Hensiktsfull Hare ',
  'Søknad om fritak fra arbeidsgiverperioden – gravid ansatt Konkurransedyktig Fisk'
]

const TidslinjeElement = (navn) => {
  const merkelapp = casual.random_key(eksempler)
  const tekst = casual.random_element(eksempler[merkelapp])
  const erUtgåttOppgave = navn === 'Oppgave' && casual.boolean
  const tilstand = erUtgåttOppgave ? 'UTGAATT' : casual.random_element(['NY', 'UTFOERT'])
  const paaminnelseTidspunkt = casual.boolean ? casualDate().toISOString() : null
  const opprettetTidspunkt = casualDate().toISOString()
  const startTidspunkt = casual.boolean ? utgåttDate().toISOString() : casualFutureDate().toISOString()
  const sluttTidspunkt = casual.boolean ? datePlusTimer(new Date(startTidspunkt), 1).toISOString() : null
  return {
    __typename: navn,
    id: Math.random().toString(36),
    tekst: tekst,

    ...(navn === 'BeskjedTidslinjeElement'
        ? {
          opprettetTidspunkt: opprettetTidspunkt
        }
        : {}
    ),
    ...(navn === 'OppgaveTidslinjeElement'
        ? {
          tilstand: tilstand,
          paaminnelseTidspunkt: paaminnelseTidspunkt,
          utgaattTidspunkt: erUtgåttOppgave ? utgåttDate().toISOString() : null,
          utfoertTidspunkt: tilstand === 'UTFOERT' ? utgåttDate().toISOString() : null,
          frist: casual.boolean ? casualDate().toISOString() : null,
          opprettetTidspunkt: opprettetTidspunkt
        }
        : {}
    ),
    ...(navn === 'KalenderavtaleTidslinjeElement'
        ? {
          tekst: 'Dialogmøte ' + casual.random_element(['Mikke', 'Minni', 'Dolly', 'Donald', 'Langbein']),
          startTidspunkt: startTidspunkt,
          sluttTidspunkt: sluttTidspunkt,
          lokasjon: casual.boolean ? null : {
            adresse: 'Thorvald Meyers gate 2B',
            postnummer: '0473',
            poststed: 'Oslo'
          },
          digitalt: casual.boolean,
          avtaletilstand: casual.random_element(['VENTER_SVAR_FRA_ARBEIDSGIVER', 'ARBEIDSGIVER_HAR_GODTATT', 'ARBEIDSGIVER_VIL_AVLYSE', 'ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED', 'AVLYST'])
        }
        : {}
    )
  }
}

const Notifikasjon = (navn) => {
  const merkelapp = casual.random_key(eksempler)
  const tekst = casual.random_element(eksempler[merkelapp])
  const erUtgåttOppgave = navn === 'Oppgave' && casual.boolean
  const tilstand = navn === 'Oppgave' ? { tilstand: erUtgåttOppgave ? 'UTGAATT' : casual.random_element(['NY', 'UTFOERT']) } : {}
  const opprettetTidspunkt = casualDate().toISOString()
  const paaminnelseTidspunkt = casual.boolean ? casualDate().toISOString() : null
  const startTidspunkt = casual.boolean ? utgåttDate().toISOString() : casualFutureDate().toISOString()
  const sluttTidspunkt = casual.boolean ? datePlusTimer(new Date(startTidspunkt), 1).toISOString() : null
  return {
    __typename: navn,
    id: Math.random().toString(36),
    merkelapp,
    tekst,
    lenke: `#${casual.word}`,
    opprettetTidspunkt: opprettetTidspunkt,

    ...(navn === 'Oppgave'
        ? {
          utgaattTidspunkt: erUtgåttOppgave ? casualDate().toISOString() : null,
          paaminnelseTidspunkt: paaminnelseTidspunkt,
          frist: casual.boolean ? casualDate().toISOString() : null
        }
        : {}
    ),
    ...(navn === 'Kalenderavtale' ? {
          tekst: 'Dialogmøte Dolly',
          startTidspunkt: startTidspunkt,
          sluttTidspunkt: sluttTidspunkt,
          lokasjon: {
            adresse: 'Thorvald Meyers gate 2B',
            postnummer: '0473',
            poststed: 'Oslo'
          },
          digitalt: casual.boolean,
          avtaletilstand: casual.random_element(['VENTER_SVAR_FRA_ARBEIDSGIVER', 'ARBEIDSGIVER_HAR_GODTATT', 'ARBEIDSGIVER_VIL_AVLYSE', 'ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED', 'AVLYST'])
        }
        : {}
    ),

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
    sak: casual.boolean ? { tittel: casual.random_element(saker) } : null
  }
}

const mocks = () => ({
  Query: () => ({
    notifikasjoner: () => ({
      notifikasjoner: [...new Array(10)]
        .map(_ => Notifikasjon(casual.random_element(['Oppgave', 'Beskjed', 'Kalenderavtale'])))
        .sort((a, b) => b.sorteringTidspunkt.localeCompare(a.sorteringTidspunkt)),
      feilAltinn: false,
      feilDigiSyfo: false
    }),
    saker: () => ({
      saker: [...new Array(30)].map(() =>
        casual.random_element(saker)
      )
        .map((tittel) => (
          {
            tittel,
            lenke: '#',
            virksomhet: { navn: 'Gamle Fredikstad og Riksdalen regnskap' },
            tidslinje: [...new Array(casual.integer(0, 3))]
              .map(_ => TidslinjeElement(casual.random_element(['OppgaveTidslinjeElement', 'BeskjedTidslinjeElement', 'KalenderavtaleTidslinjeElement']))),
            sisteStatus: {
              tekst: casual.random_element(['Mottatt', 'Under behandling', 'Utbetalt']),
              tidspunkt: casualDate().toISOString()
            },
            nesteSteg: casual.random_element(["Saksbehandlingstiden er lang. Du kan forvente refusjon utbetalt i januar 2025.", "Denne saken vil bli behandlet innen 1. juli.", "Denne saken blir nok ikke behandlet.", ...new Array(7).fill(null)]),
            frister: casual.boolean ? [
              casual.random_element([null, casualDate().toISOString().slice(0, 10)]),
              casual.random_element([null, new Date().toISOString().replace(/T.*/, '')])
            ] : []

          })),
      totaltAntallSaker: 314,
      sakstyper: Object.keys
      (eksempler).map(navn => ({ navn, antall: casual.integer(0, 10) }))
    }),
    sakstyper: Object.keys(eksempler).map(navn => ({ navn }))
  }),
  Int: () => casual.integer(0, 1000),
  String: () => casual.string,
  ISO8601DateTime: () => roundDate(5000).toISOString(),
  ISO8601Date: () => roundDate(5000).toISOString().slice(0, 10),
  Virksomhet: () => ({ navn: casual.catch_phrase }),
  KalenderavtalerResultat: () => ({
    avtaler: [
      {
        tekst: 'Dialogmøte Mikke',
        startTidspunkt: '2021-02-04T15:15:00',
        sluttTidspunkt: null,
        lokasjon: {
          adresse: 'Thorvald Meyers gate 2B',
          postnummer: '0473',
          poststed: 'Oslo'
        },
        avtaletilstand: 'ARBEIDSGIVER_VIL_AVLYSE',
        digitalt: false
      },
      {
        tekst: 'Dialogmøte Minni',
        startTidspunkt: '2021-02-04T15:15:00',
        sluttTidspunkt: null,
        avtaletilstand: 'ARBEIDSGIVER_HAR_GODTATT',
        digitalt: true,
        lokasjon: null
      },
      {
        tekst: 'Dialogmøte Dolly',
        startTidspunkt: '2021-02-04T15:15:00',
        sluttTidspunkt: '2021-02-04T16:15:00',
        avtaletilstand: 'ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED',
        digitalt: false,
        lokasjon: {
          adresse: 'Thorvald Meyers gate 2B',
          postnummer: '0473',
          poststed: 'Oslo'
        }
      },
      {
        tekst: 'Dialogmøte Donald',
        startTidspunkt: '2021-02-04T15:15:00',
        sluttTidspunkt: null,
        avtaletilstand: 'VENTER_SVAR_FRA_ARBEIDSGIVER',
        lokasjon: null,
        digitalt: false
      },
      {
        tekst: 'Dialogmøte Langbein',
        startTidspunkt: '2021-02-04T15:15:00',
        sluttTidspunkt: '2021-02-04T16:15:00',
        avtaletilstand: 'AVLYST',
        lokasjon: null
      }
    ]
  })
})

const createApolloServer = ({ mocks: apolloServerOptionsMocks, ...apolloServerOptions } = {}) => {
  const { ApolloServer, gql } = require('apollo-server-express')
  casual = require('casual')

  const data = fs.readFileSync(path.join(__dirname, 'bruker.graphql'))
  return new ApolloServer({
    typeDefs: gql(data.toString()),
    mocks: { ...mocks(), ...apolloServerOptionsMocks },
    ...apolloServerOptions
  })
}

function applyNotifikasjonMockMiddleware(middlewareOptions, apolloServerOptions) {
  const apolloServer = createApolloServer(apolloServerOptions)
  apolloServer.start()
    .then(() => {
      apolloServer.applyMiddleware(middlewareOptions)
    })
    .catch(error =>
      console.log('error starting apollo server', { error })
    )
}

module.exports = {
  createApolloServer,
  applyNotifikasjonMockMiddleware
}
