import amplitude, {AmplitudeClient} from 'amplitude-js'
import {Notifikasjon} from '../api/graphql-types'
import React, {createContext, ReactNode, useContext, useMemo} from "react";
import {Miljø} from "../index";

const createAmpltiudeInstance = (apiKey: string) => {
  const instance = amplitude.getInstance()
  instance.init(apiKey, '', {
    apiEndpoint: 'amplitude.nav.no/collect',
    saveEvents: false,
    includeUtm: true,
    batchEvents: false,
    includeReferrer: true
  })
  return instance
}

const getLimitedUrl = () => {
  const {origin, pathname } = window.location;
  return `${origin}/${pathname.split('/')[1]}`;
}

const createAmplitudeLogger = (amplitudeClient: AmplitudeClient) => ({
  loggLasting: (antallNotifikasjoner: number, ulesteNotifikasjoner: number) => {
    amplitudeClient.logEvent('last-komponent', {
      tittel: 'notifikasjons-widget',
      url: getLimitedUrl(),
      'antall-notifikasjoner': antallNotifikasjoner,
      'antall-ulestenotifikasjoner': ulesteNotifikasjoner,
      'antall-lestenotifikasjoner': antallNotifikasjoner - ulesteNotifikasjoner
    })
  },

  loggÅpning: (antallNotifikasjoner: number, ulesteNotifikasjoner: number) => {
    amplitudeClient.logEvent('panel-ekspander', {
      tittel: 'arbeidsgiver notifikasjon panel',
      url: getLimitedUrl(),
      'antall-notifikasjoner': antallNotifikasjoner,
      'antall-ulestenotifikasjoner': ulesteNotifikasjoner,
      'antall-lestenotifikasjoner': antallNotifikasjoner - ulesteNotifikasjoner
    })
  },

  loggLukking: () => {
    amplitudeClient.logEvent('panel-kollaps', {
      tittel: 'arbeidsgiver notifikasjon panel',
      url: getLimitedUrl(),
    })
  },

  loggPilTastNavigasjon: () => {
    amplitudeClient.logEvent('piltast-navigasjon', {
      url: getLimitedUrl(),
    })
  },

  loggNotifikasjonKlikk: (notifikasjon: Notifikasjon, index: number) => {
    const klikketPaaTidligere = notifikasjon.brukerKlikk.klikketPaa
    amplitudeClient.logEvent('notifikasjon-klikk', {
      url: getLimitedUrl(),
      index: index,
      'merkelapp': notifikasjon.merkelapp,
      'klikket-paa-tidligere': klikketPaaTidligere,
      'destinasjon': notifikasjon.lenke
    })
  }
})

const stubbedAmplitudeClient = {
  logEvent: (event: string, data?: any) => {
    console.log(`${event}: ${JSON.stringify(data)}`, {event, data})
  },
  setUserProperties: (userProps: object) => {
    console.log(`set userprops: ${JSON.stringify(userProps)}`)
  }
} as amplitude.AmplitudeClient

const AmplitudeContext = createContext(createAmplitudeLogger(stubbedAmplitudeClient))

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


const useAmplitudeClient = (miljø: Miljø): AmplitudeClient => {
  return useMemo(
    () => {
      switch (miljø) {
        case 'prod':
          return createAmpltiudeInstance('a8243d37808422b4c768d31c88a22ef4');
        case 'dev':
          return createAmpltiudeInstance('6ed1f00aabc6ced4fd6fcb7fcdc01b30');
        default:
          return stubbedAmplitudeClient
      }
    }, [miljø]
  )
}
