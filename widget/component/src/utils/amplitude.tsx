import * as amplitude from '@amplitude/analytics-browser';
import {Types} from '@amplitude/analytics-browser';
import {Notifikasjon} from '../api/graphql-types'
import React, {createContext, ReactNode, useContext, useMemo} from "react";
import {Miljø} from "../index";

type AmplitudeInstance = Pick<Types.BrowserClient, 'logEvent'>;
const createAmpltiudeInstance = (apiKey: string): AmplitudeInstance => {
  amplitude
    .init(apiKey, undefined, {
      serverUrl: 'https://amplitude.nav.no/collect',
      useBatch: false,
      autocapture: {
        attribution: true,
        fileDownloads: false,
        formInteractions: false,
        pageViews: true,
        sessions: true,
        elementInteractions: false,
      },
    })
    .promise.catch((error) => {
    console.error('error initializing amplitude', error);
  });
  return amplitude;
};

const getLimitedUrl = () => {
  const {origin, pathname } = window.location;
  return `${origin}/${pathname.split('/')[1]}`;
}

const createAmplitudeLogger = (instance: AmplitudeInstance) => ({
  loggLasting: (antallNotifikasjoner: number, ulesteNotifikasjoner: number) => {
    instance.logEvent('last-komponent', {
      tittel: 'notifikasjons-widget',
      url: getLimitedUrl(),
      'antall-notifikasjoner': antallNotifikasjoner,
      'antall-ulestenotifikasjoner': ulesteNotifikasjoner,
      'antall-lestenotifikasjoner': antallNotifikasjoner - ulesteNotifikasjoner
    })
  },

  loggÅpning: (antallNotifikasjoner: number, ulesteNotifikasjoner: number) => {
    instance.logEvent('panel-ekspander', {
      tittel: 'arbeidsgiver notifikasjon panel',
      url: getLimitedUrl(),
      'antall-notifikasjoner': antallNotifikasjoner,
      'antall-ulestenotifikasjoner': ulesteNotifikasjoner,
      'antall-lestenotifikasjoner': antallNotifikasjoner - ulesteNotifikasjoner
    })
  },

  loggLukking: () => {
    instance.logEvent('panel-kollaps', {
      tittel: 'arbeidsgiver notifikasjon panel',
      url: getLimitedUrl(),
    })
  },

  loggPilTastNavigasjon: () => {
    instance.logEvent('piltast-navigasjon', {
      url: getLimitedUrl(),
    })
  },

  loggNotifikasjonKlikk: (notifikasjon: Notifikasjon, index: number) => {
    const klikketPaaTidligere = notifikasjon.brukerKlikk.klikketPaa
    instance.logEvent('notifikasjon-klikk', {
      url: getLimitedUrl(),
      index: index,
      'merkelapp': notifikasjon.merkelapp,
      'klikket-paa-tidligere': klikketPaaTidligere,
      'destinasjon': notifikasjon.lenke
    })
  }
})

const mockedAmplitude = (): AmplitudeInstance => ({
  logEvent: (eventInput: Types.BaseEvent | string, eventProperties?: Record<string, any>) => {
    console.group('Mocked amplitude-event');
    console.table({ eventInput, ...eventProperties });
    console.groupEnd();
    return {
      promise: new Promise<Types.Result>((resolve) =>
        resolve({
          event: { event_type: 'MockEvent' },
          code: 200,
          message: 'Success: mocked amplitude-tracking',
        })
      ),
    };
  },
});

const AmplitudeContext = createContext(createAmplitudeLogger(mockedAmplitude()))

type Props = {
  miljo: Miljø,
  children: ReactNode,
}

export const AmplitudeProvider = ({miljo, children}: Props) => {
  const client = useAmplitudeClient(miljo)
  const logger = createAmplitudeLogger(client)
  return <AmplitudeContext.Provider value={logger}>
    {children}
  </AmplitudeContext.Provider>
}

export function useAmplitude() {
  return useContext(AmplitudeContext)
}


const useAmplitudeClient = (miljø: Miljø): AmplitudeInstance => {
  return useMemo(
    () => {
      switch (miljø) {
        case 'prod':
          return createAmpltiudeInstance('a8243d37808422b4c768d31c88a22ef4');
        case 'dev':
          return createAmpltiudeInstance('6ed1f00aabc6ced4fd6fcb7fcdc01b30');
        default:
          return mockedAmplitude()
      }
    }, [miljø]
  )
}
