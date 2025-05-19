import { CSSProperties, useCallback, useEffect, useRef, useState } from 'react';
import { NotifikasjonBjelle } from './NotifikasjonBjelle/NotifikasjonBjelle';
import NotifikasjonPanel from './NotifikasjonPanel/NotifikasjonPanel';
import { ServerError, useQuery } from '@apollo/client';
import { HENT_NOTIFIKASJONER } from '../api/graphql';
import { filtrerUlesteNotifikasjoner } from '../utils/filtrerUlesteNotifikasjoner';
import { useOnClickOutside } from '../hooks/useOnClickOutside';
import { useAnalytics } from '../context/AnalyticsProvider';
import { getLimitedUrl } from '../utils/utils';
import { Dropdown } from '@navikt/ds-react';
import { useNotifikasjonerSistLest } from '../hooks/useNotifikasjonerSistLest';

const NotifikasjonWidget = () => {
    const logEvent = useAnalytics();

    const {
      data,
      error,
      stopPolling,
    } = useQuery(
      HENT_NOTIFIKASJONER,
      {
        pollInterval: 60_000,
      },
    );

    useEffect(() => {
      if (error) {
        console.error('Error fetching notifications:', error);
        if ((error.networkError as ServerError)?.statusCode === 401) {
          console.log('stopper poll pga 401 unauthorized');
          stopPolling();
        }
      }
    }, [error]);

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

    const { sistLest, setSistLest, mutationNotifikasjonerSistLest } = useNotifikasjonerSistLest();

    const setRemoteSistLest = useCallback(() => {
      if (notifikasjoner && notifikasjoner.length > 0) {
        // naiv impl forutsetter sortering
        mutationNotifikasjonerSistLest({
          variables: { tidspunkt: notifikasjoner[0].sorteringTidspunkt },
        });
      }
    }, [notifikasjoner]);

    const antallUleste = notifikasjoner && filtrerUlesteNotifikasjoner(sistLest, notifikasjoner).length;
    const widgetRef = useRef<HTMLDivElement>(null);
    const bjelleRef = useRef<HTMLButtonElement>(null);
    const [erApen, setErApen] = useState(false);

    useEffect(() => {
      if (notifikasjoner !== undefined && antallUleste !== undefined) {
        trackLasting(notifikasjoner.length, antallUleste);
      }
    }, [notifikasjoner, antallUleste]);

    const togglePanel = () => {
      if (erApen) {
        trackLukking();
        notifikasjoner && setSistLest(notifikasjoner[0].sorteringTidspunkt);
        setErApen(false);
        bjelleRef.current?.focus();
      } else {
        if (!notifikasjoner || notifikasjoner?.length === 0) return;
        setRemoteSistLest();
        trackÅpning(notifikasjoner?.length ?? 0, antallUleste ?? 0);
        setErApen(true);
      }
    };

    useOnClickOutside(widgetRef, () => {
      erApen && togglePanel();
    });

    const style: CSSProperties = notifikasjoner === undefined || notifikasjoner.length === 0 ? { visibility: 'hidden' } : {};

    return <div ref={widgetRef} style={style}>
      <Dropdown
        open={erApen}
      >
        <NotifikasjonBjelle
          antallUleste={antallUleste}
          erApen={erApen}
          focusableRef={bjelleRef}
          onClick={togglePanel}
        />
        <NotifikasjonPanel
          notifikasjoner={notifikasjonerResultat ?? {
            notifikasjoner: [],
            feilAltinn: false,
            feilDigiSyfo: false,
          }}
          erApen={erApen}
          togglePanel={togglePanel}
        />
      </Dropdown>
    </div>;
  }
;

export default NotifikasjonWidget;
