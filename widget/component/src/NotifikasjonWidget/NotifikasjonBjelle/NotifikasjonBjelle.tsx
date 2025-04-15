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

const NotifikasjonBjelleUleste = ({
                                    antallUleste = 0,
                                    erApen,
                                    onClick,
                                    focusableRef,
                                  }: Props) => {
  return (
    <div className={`notifikasjon_bjelle uleste ${erApen ? "er_apen" : ""}`}>
      <button
        ref={focusableRef}
        onClick={onClick}
        className={`notifikasjon_bjelle-knapp uleste`}
        aria-label={`Dine varsler, ${antallUleste} nye.`}
        aria-owns="notifikasjon_panel"
        aria-haspopup="dialog"
        aria-pressed={erApen}
        aria-live="polite"
        aria-atomic="true"
      >
        <div className={`notifikasjon_bjelle-ikon`}>
          <BellFillIcon width="28px" height="28px" aria-hidden="true" />
          <div
            className={'notifikasjon_bjelle-ikon__ulest-sirkel'}
          >
            <BodyShort className="notifikasjon_bjelle-ikon__ulest-antall">
              {antallUleste < 10 ? antallUleste : '9+'}
            </BodyShort>
          </div>
        </div>
        <BodyShort size="small">Varsler</BodyShort>
      </button>
    </div>
  );
};

const NotifikasjonBjelleIngenUleste = ({
                                         erApen,
                                         onClick,
                                         focusableRef,
                                       }: Props) => {

  return (
    <div className={`notifikasjon_bjelle ingen_uleste ${erApen ? "er_apen" : ""}`}>
      <button
        ref={focusableRef}
        onClick={onClick}
        className={`notifikasjon_bjelle-knapp ingen_uleste`}
        aria-label={`Dine varsler, ingen nye.`}
        aria-owns="notifikasjon_panel"
        aria-haspopup="dialog"
        aria-pressed={erApen}
        aria-live="polite"
        aria-atomic="true"
      >
        <div className={`notifikasjon_bjelle-ikon`}>
          <BellFillIcon width="28px" height="28px" aria-hidden="true" />
        </div>
        <BodyShort size="small">Varsler</BodyShort>
      </button>
    </div>
  );
};


export const NotifikasjonBjelle = ({
                                     antallUleste = 0,
                                     erApen,
                                     onClick,
                                     focusableRef,
                                   }: Props) => {
  if (antallUleste > 0) {
    return <NotifikasjonBjelleUleste antallUleste={antallUleste} erApen={erApen} onClick={onClick}
                                     focusableRef={focusableRef} />;
  }
  return <NotifikasjonBjelleIngenUleste erApen={erApen} onClick={onClick} focusableRef={focusableRef} />;
};
