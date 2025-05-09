import React, { CSSProperties, useCallback, useEffect, useRef, useState } from 'react';
import { NotifikasjonBjelle } from './NotifikasjonBjelle/NotifikasjonBjelle';
import NotifikasjonPanel from './NotifikasjonPanel/NotifikasjonPanel';
import { ServerError, useQuery } from '@apollo/client';
import { HENT_NOTIFIKASJONER } from '../api/graphql';
import useLocalStorage from '../hooks/useLocalStorage';
import Dropdown from './NotifikasjonPanel/Dropdown';
import { filtrerUlesteNotifikasjoner } from '../utils/filtrerUlesteNotifikasjoner';
import { useOnClickOutside } from '../hooks/useOnClickOutside';
import { useAnalytics } from '../context/AnalyticsProvider';
import { getLimitedUrl } from '../utils/utils';

const NotifikasjonWidget = () => {
    const logEvent = useAnalytics();

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
      logEvent('panel-kollaps', {
        tittel: 'arbeidsgiver notifikasjon panel',
        url: getLimitedUrl(),
      });
    }

    function trackLasting(antallNotifikasjoner: number, antallUlesteNotifikasjoner: number) {
      logEvent('last-komponent', {
        tittel: 'arbeidsgiver notifikasjon panel',
        url: getLimitedUrl(),
        'antall-notifikasjoner': antallNotifikasjoner,
        'antall-ulestenotifikasjoner': antallUlesteNotifikasjoner,
        'antall-lestenotifikasjoner': antallNotifikasjoner - antallUlesteNotifikasjoner,
      });
    }

    function trackÅpning(antallNotifikasjoner: number, antallUlesteNotifikasjoner: number) {
      logEvent('panel-ekspander', {
        tittel: 'arbeidsgiver notifikasjon panel',
        url: getLimitedUrl(),
        'antall-notifikasjoner': antallNotifikasjoner,
        'antall-ulestenotifikasjoner': antallUlesteNotifikasjoner,
        'antall-lestenotifikasjoner': antallNotifikasjoner - antallUlesteNotifikasjoner,
      });
    }

    const notifikasjonerResultat = data?.notifikasjoner;
    const notifikasjoner = notifikasjonerResultat?.notifikasjoner;

    const [lagretSistLest, setLagretSistLest] = useLocalStorage<string | undefined>(
      'sist_lest',
      undefined,
    );
    const [synligSistLest, setSynligSistLest] = useState(lagretSistLest);

    const setSistLest = useCallback(() => {
      if (notifikasjoner && notifikasjoner.length > 0) {
        // naiv impl forutsetter sortering
        setLagretSistLest(notifikasjoner[0].sorteringTidspunkt);
      }
    }, [notifikasjoner]);

    const antallUleste = notifikasjoner && filtrerUlesteNotifikasjoner(synligSistLest, notifikasjoner).length;
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
        notifikasjoner && setSynligSistLest(notifikasjoner[0].sorteringTidspunkt);
        setErApen(false);
      }
    };

    const åpnePanelMedLogging = (antallNotifikasjoner: number, antallUlesteNotifikasjoner: number) => {
      setSistLest();
      trackÅpning(antallNotifikasjoner, antallUlesteNotifikasjoner);
      setErApen(true);
    };

    useOnClickOutside(widgetRef, () => {
      lukkÅpentPanelMedLogging();
    });

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
