import React, { Ref } from 'react';
import './NotifikasjonBjelle.css';
import { BodyShort } from '@navikt/ds-react';
import { BellFillIcon } from '@navikt/aksel-icons';

interface Props {
  antallUleste?: number;
  erApen: boolean;
  onClick?: () => void;
  focusableRef: Ref<HTMLButtonElement>;
}

export const NotifikasjonBjelle = ({
                                     antallUleste = 0,
                                     erApen,
                                     onClick,
                                     focusableRef,
                                   }: Props) => {

  return (
    <div className={`notifikasjon_bjelle ${antallUleste > 0 ? 'notifikasjon_bjelle_uleste' : ''}`}>
      <button
        ref={focusableRef}
        onClick={onClick}
        className={`notifikasjon_bjelle-knapp`}
        aria-label={`Dine varsler, ${antallUleste} nye.`}
        aria-owns="notifikasjon_panel"
        aria-haspopup="dialog"
        aria-pressed={erApen}
        aria-live="polite"
        aria-atomic="true"
      >
        <div className={`notifikasjon_bjelle-ikon`}>
          {antallUleste === 0 ?
            (
              <BellFillIcon width="28px" height="28px" aria-hidden="true" />
            ) : (
              <>
                  <BellFillIcon width="28px" height="28px" aria-hidden="true" />
                <div
                  className={`notifikasjon_bjelle-ikon__ulest-sirkel ${
                    antallUleste === 0
                      ? 'notifikasjon_bjelle-ikon__ulest-sirkel--hide'
                      : ''
                  }`}
                >
                  <BodyShort className="notifikasjon_bjelle-ikon__ulest-antall">
                    {antallUleste < 10 ? antallUleste : '9+'}
                  </BodyShort>
                </div>
              </>
            )
          }
        </div>
        <BodyShort size="small">Varsler</BodyShort>
      </button>
    </div>
  );
}
