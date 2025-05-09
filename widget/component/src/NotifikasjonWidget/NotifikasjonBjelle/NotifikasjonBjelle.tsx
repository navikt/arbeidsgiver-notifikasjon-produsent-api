import React, { Ref } from 'react';
import './NotifikasjonBjelle.css';
import { BodyShort } from '@navikt/ds-react';
import { BellFillIcon } from '@navikt/aksel-icons';

interface NotifikasjonBjelleProps {
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
                                   }: NotifikasjonBjelleProps) => {
  const harUleste = antallUleste > 0;

  return (
    <div className={`notifikasjon_bjelle ${harUleste ? 'uleste' : 'ingen_uleste'} ${erApen ? 'er_apen' : ''}`}>
      <button
        ref={focusableRef}
        onClick={onClick}
        className={`notifikasjon_bjelle-knapp ${harUleste ? 'uleste' : 'ingen_uleste'}`}
        aria-label={
          harUleste
            ? `Dine varsler, ${antallUleste} nye.`
            : 'Dine varsler, ingen nye.'
        }
        aria-owns="notifikasjon_panel"
        aria-haspopup="dialog"
        aria-pressed={erApen}
        aria-live="polite"
        aria-atomic="true"
      >
        <div className="notifikasjon_bjelle-ikon">
          <BellFillIcon width="28px" height="28px" aria-hidden="true" />
          {harUleste && (
            <div className="notifikasjon_bjelle-ikon__ulest-sirkel">
              <BodyShort className="notifikasjon_bjelle-ikon__ulest-antall">
                {antallUleste < 10 ? antallUleste : '9+'}
              </BodyShort>
            </div>
          )}
        </div>
        <BodyShort size="small">Varsler</BodyShort>
      </button>
    </div>
  );
};
