import React, { CSSProperties, useCallback, useEffect, useRef, useState } from 'react';
import { NotifikasjonBjelle } from './NotifikasjonBjelle/NotifikasjonBjelle';
import NotifikasjonPanel from './NotifikasjonPanel/NotifikasjonPanel';
import { ServerError, useQuery } from '@apollo/client';
import { HENT_NOTIFIKASJONER } from '../api/graphql';
import useLocalStorage from '../hooks/useLocalStorage';
import { Notifikasjon, OppgaveTilstand } from '../api/graphql-types';
import { getLimitedUrl, useAmplitude } from '../utils/amplitude';
import Dropdown from './NotifikasjonPanel/Dropdown';

const uleste = (
  sistLest: string | undefined,
  notifikasjoner: Notifikasjon[],
): Notifikasjon[] => {
  if (sistLest === undefined) {
    return notifikasjoner;
  } else {
    return notifikasjoner.filter((notifikasjon) => {
      if (notifikasjon.__typename === 'Oppgave' && notifikasjon.tilstand !== OppgaveTilstand.Ny) {
        return false;
      }
      return new Date(notifikasjon.sorteringTidspunkt).getTime() > new Date(sistLest).getTime();
    });
  }
};

type Props = {
  notifikasjonWidgetUmami?: {
    track: (eventName: string, eventData?: Record<string, any>) => void;
  }
}

const NotifikasjonWidget = ({ notifikasjonWidgetUmami }: Props) => {
    const { loggLukking, loggLasting, loggÅpning } = useAmplitude();
    const [sistLest, _setSistLest] = useLocalStorage<string | undefined>(
      'sist_lest',
      undefined,
    );

    const {
      previousData,
      data = previousData,
      stopPolling,
    } = useQuery(
      HENT_NOTIFIKASJONER,
      {
        pollInterval: 60_000,
        onError(e) {
          if ((e.networkError as ServerError)?.statusCode === 401) {
            console.log('stopper poll pga 401 unauthorized');
            stopPolling();
          }
        },
      },
    );

    function trackLukking() {
      notifikasjonWidgetUmami?.track('panel-kollaps', {
        tittel: 'arbeidsgiver notifikasjon panel',
        url: getLimitedUrl(),
      });
      loggLukking();
    }

    function trackLasting(antallNotifikasjoner: number, antallUlesteNotifikasjoner: number) {
      notifikasjonWidgetUmami?.track('last-komponent', {
        tittel: 'arbeidsgiver notifikasjon panel',
        url: getLimitedUrl(),
        'antall-notifikasjoner': antallNotifikasjoner,
        'antall-ulestenotifikasjoner': antallUlesteNotifikasjoner,
        'antall-lestenotifikasjoner': antallNotifikasjoner - antallUlesteNotifikasjoner,
      });
      loggLasting(antallNotifikasjoner, antallUlesteNotifikasjoner);
    }

    function trackÅpning(antallNotifikasjoner: number, antallUlesteNotifikasjoner: number) {
      notifikasjonWidgetUmami?.track('panel-ekspander', {
        tittel: 'arbeidsgiver notifikasjon panel',
        url: getLimitedUrl(),
        'antall-notifikasjoner': antallNotifikasjoner,
        'antall-ulestenotifikasjoner': antallUlesteNotifikasjoner,
        'antall-lestenotifikasjoner': antallNotifikasjoner - antallUlesteNotifikasjoner,
      });
      loggÅpning(antallNotifikasjoner, antallUlesteNotifikasjoner);
    }

    const notifikasjonerResultat = data?.notifikasjoner;
    const notifikasjoner = notifikasjonerResultat?.notifikasjoner;
    const setSistLest = useCallback(() => {
      if (notifikasjoner && notifikasjoner.length > 0) {
        // naiv impl forutsetter sortering
        _setSistLest(notifikasjoner[0].sorteringTidspunkt);
      }
    }, [notifikasjoner]);

    const antallUleste = notifikasjoner && uleste(sistLest, notifikasjoner).length;
    const widgetRef = useRef<HTMLDivElement>(null);
    const bjelleRef = useRef<HTMLButtonElement>(null);
    const [erApen, setErApen] = useState(false);
    useEffect(() => {
      if (notifikasjoner !== undefined && antallUleste !== undefined) {
        trackLasting(notifikasjoner.length, antallUleste);
      }
    }, [notifikasjoner, antallUleste]);
    const lukkÅpentPanelMedLogging = () => {
      if (erApen) {
        trackLukking();
        setSistLest();
        setErApen(false);
      }
    };
    const åpnePanelMedLogging = (antallNotifikasjoner: number, antallUlesteNotifikasjoner: number) => {
      trackÅpning(antallNotifikasjoner, antallUlesteNotifikasjoner);
      setErApen(true);
    };

    const handleFocusOutside: { (event: MouseEvent | KeyboardEvent): void } = (
      e: MouseEvent | KeyboardEvent,
    ) => {
      const node = widgetRef.current;
      // @ts-ignore
      if (node && node !== e.target && node.contains(e.target as HTMLElement)) {
        return;
      }
      lukkÅpentPanelMedLogging();
    };

    useEffect(() => {
      document.addEventListener('click', handleFocusOutside);
      return () => {
        document.removeEventListener('click', handleFocusOutside);
      };
    }, [handleFocusOutside]);

    const style: CSSProperties = notifikasjoner === undefined || notifikasjoner.length === 0 ? { visibility: 'hidden' } : {};

    return <div ref={widgetRef} style={style}>
      <NotifikasjonBjelle
        antallUleste={antallUleste}
        erApen={erApen}
        focusableRef={bjelleRef}
        onClick={() => {
          if (notifikasjoner !== undefined && antallUleste !== undefined) { // er invisible hvis dette er false. se style
            erApen ? lukkÅpentPanelMedLogging() : åpnePanelMedLogging(notifikasjoner.length, antallUleste);
          }
        }}
      />
      <Dropdown
        erApen={erApen}
        ariaLabelledby="notifikasjon_panel-header"
      >
        <NotifikasjonPanel
          notifikasjoner={notifikasjonerResultat ?? {
            notifikasjoner: [],
            feilAltinn: false,
            feilDigiSyfo: false,
          }}
          erApen={erApen}
          onLukkPanel={() => {
            lukkÅpentPanelMedLogging();
            bjelleRef.current?.focus();
          }}
        />
      </Dropdown>
    </div>;
  }
;

export default NotifikasjonWidget;
