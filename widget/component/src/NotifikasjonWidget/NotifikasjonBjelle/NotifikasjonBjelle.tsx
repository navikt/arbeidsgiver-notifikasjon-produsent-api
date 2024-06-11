import React, { Ref } from 'react'
import './NotifikasjonBjelle.css'
import {BodyShort, Label} from "@navikt/ds-react";
import {Bell} from "@navikt/ds-icons";

interface Props {
  antallUleste?: number
  erApen: boolean
  onClick?: () => void
  focusableRef: Ref<HTMLButtonElement>
}

export const NotifikasjonBjelle = ({
  antallUleste = 0,
  erApen,
  onClick,
  focusableRef
}: Props) => {
  return (
    <div className='notifikasjon_bjelle'>
      <button
        ref={focusableRef}
        onClick={onClick}
        className={`notifikasjon_bjelle-knapp notifikasjon_bjelle-knapp${
          erApen ? '--open' : ''
        }`}
        aria-label={`Dine varsler, ${antallUleste} nye.`}
        aria-owns='notifikasjon_panel'
        aria-haspopup='dialog'
        aria-pressed={erApen}
        aria-live="polite"
        aria-atomic="true"
      >
        <div className='notifikasjon_bjelle-ikon'>
          <Bell width="32px" height="32px" aria-hidden="true" />

          <div
            className={`notifikasjon_bjelle-ikon__ulest-sirkel ${
              antallUleste === 0
                ? 'notifikasjon_bjelle-ikon__ulest-sirkel--hide'
                : ''
            }`}
          >
            <Label className='notifikasjon_bjelle-ikon__ulest-antall'>
              {antallUleste < 10 ? antallUleste : '9+'}
            </Label>
          </div>
        </div>
        <BodyShort size="small">Varsler</BodyShort>

      </button>
      <div
        className={`notifikasjon_bjelle-understrek ${
          erApen ? '' : 'notifikasjon_bjelle-understrek--closed'
        }`}
      />
    </div>
  )
}
