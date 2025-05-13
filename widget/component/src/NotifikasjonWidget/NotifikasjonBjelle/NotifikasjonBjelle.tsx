import { Ref } from 'react';
import './NotifikasjonBjelle.css';
import { Dropdown, BodyShort, Button } from '@navikt/ds-react';
import { BellFillIcon } from '@navikt/aksel-icons';

interface NotifikasjonBjelleProps {
  antallUleste?: number;
  erApen: boolean;
  onClick: () => void;
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
      <Button
        as={Dropdown.Toggle}
        variant={harUleste ? 'primary' : 'secondary'}
        ref={focusableRef}
        onClick={onClick}
        aria-label={
          harUleste
            ? `${antallUleste} nye varsler.`
            : 'Ingen nye varsler.'
        }
        aria-expanded={erApen}
        aria-controls="notifikasjon-utvidet-innhold"
        aria-live="polite"
      >
        <div className="notifikasjon-bjelle">
          <BellFillIcon fontSize="2rem" aria-hidden />
          {harUleste && (
            <span className="notifikasjon-badge" aria-hidden="true">
                {antallUleste && antallUleste < 10 ? antallUleste : '9+'}
              </span>
          )}
          <BodyShort size="small" weight="semibold" style={{ marginTop: '0.25rem' }}>
            Varsler
          </BodyShort>
        </div>
      </Button>
  );
};
