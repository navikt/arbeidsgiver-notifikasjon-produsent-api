import { CSSProperties, useCallback, useEffect, useRef, useState } from 'react';
import { NotifikasjonBjelle } from './NotifikasjonBjelle/NotifikasjonBjelle';
import NotifikasjonPanel from './NotifikasjonPanel/NotifikasjonPanel';
import { ServerError } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { HENT_NOTIFIKASJONER } from '../api/graphql';
import { filtrerUlesteNotifikasjoner } from '../utils/filtrerUlesteNotifikasjoner';
import { useOnClickOutside } from '../hooks/useOnClickOutside';
import { Dropdown } from '@navikt/ds-react';
import { useNotifikasjonerSistLest } from '../hooks/useNotifikasjonerSistLest';

const NotifikasjonWidget = () => {
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
        if (ServerError.is(error) && error.statusCode === 401) {
          console.log('stopper poll pga 401 unauthorized');
          stopPolling();
        }
      }
    }, [error, stopPolling]);

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

    const togglePanel = () => {
      if (erApen) {
        notifikasjoner && setSistLest(notifikasjoner[0].sorteringTidspunkt);
        setErApen(false);
        bjelleRef.current?.focus();
      } else {
        if (!notifikasjoner || notifikasjoner?.length === 0) return;
        setRemoteSistLest();
        setErApen(true);
      }
    };

    useOnClickOutside(widgetRef, () => {
      erApen && togglePanel();
    });

    const style: CSSProperties = notifikasjoner === undefined || notifikasjoner.length === 0 ? { visibility: 'hidden' } : {};

    return <div ref={widgetRef} style={style}>
      <Dropdown open={erApen}>
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
