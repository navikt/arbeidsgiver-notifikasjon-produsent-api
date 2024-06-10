import React, { FC, ReactNode } from 'react'
import { Tag } from '@navikt/ds-react'
import './StatusLinje.css'
import { Notifikasjon, OppgaveTilstand } from '../../../api/graphql-types'
import { StopWatch } from '@navikt/ds-icons'
import { formatterDato, uformellDatotekst } from '../dato-funksjoner'

export interface StatusLinjeProps {
  notifikasjon: Notifikasjon
}

export const StatusLinje: FC<StatusLinjeProps> = ({ notifikasjon }) => {
  if (notifikasjon.__typename !== 'Oppgave') {
    return null
  }

  switch (notifikasjon.tilstand) {
    case OppgaveTilstand.Utfoert:
      return (
        <StatusIkonMedTekst variant='success'>
          Utført {notifikasjon.utfoertTidspunkt ? uformellDatotekst(new Date(notifikasjon.utfoertTidspunkt)) : null}
        </StatusIkonMedTekst>
      )

    case OppgaveTilstand.Utgaatt:
      return (
        <StatusIkonMedTekst variant='neutral'>
          Fristen gikk ut {uformellDatotekst(new Date(notifikasjon.utgaattTidspunkt))}
        </StatusIkonMedTekst>
      )

    case OppgaveTilstand.Ny:
      if (!notifikasjon.frist && !notifikasjon.paaminnelseTidspunkt) {
        return null
      } else {
        let innhold
        if (!notifikasjon.frist && notifikasjon.paaminnelseTidspunkt) {
          innhold = <>Påminnelse</>
        } else if (notifikasjon.frist && !notifikasjon.paaminnelseTidspunkt) {
          innhold = <>Frist {formatterDato(new Date(notifikasjon.frist))}</>
        } else {
          innhold = <>Påminnelse &ndash; Frist {formatterDato(new Date(notifikasjon.frist))}</>
        }
        return <StatusIkonMedTekst variant='warning'> {innhold} </StatusIkonMedTekst>
      }
    default:
      return null
  }
}

type StatusIkonMedTekstProps = {
  children: ReactNode;
  variant: 'success' | 'neutral' | 'warning';
}

const StatusIkonMedTekst: FC<StatusIkonMedTekstProps> = ({ variant, children }) => (
  <Tag size='small' className='notifikasjon_StatusLinje' variant={variant}>
    <span className='notifikasjon_oppgave_status_text'>
      <StopWatch aria-hidden={true} /> {children}
    </span>
  </Tag>
)
