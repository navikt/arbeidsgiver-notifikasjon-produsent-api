import React, {FC} from 'react'
import {Tag} from '@navikt/ds-react'
import './AvtaletilstandLinje.css'
import {KalenderavtaleTilstand, Notifikasjon} from '../../../api/graphql-types'

export interface StatusLinjeProps {
  notifikasjon: Notifikasjon
}

export const AvtaletilstandLinje: FC<StatusLinjeProps> = ({ notifikasjon }) => {
  if (notifikasjon.__typename !== 'Kalenderavtale') {
    return null
  }

  switch (notifikasjon.avtaletilstand) {
    case KalenderavtaleTilstand.VenterSvarFraArbeidsgiver:
      return (
        <Tag size='small' className='notifikasjon_AvtaletilstandLinje' variant='warning'>
          Svar på invitasjonen
        </Tag>
      )

    case KalenderavtaleTilstand.ArbeidsgiverHarGodtatt:
      return (
        <Tag size='small' className='notifikasjon_AvtaletilstandLinje' variant='success'>
          Du har takket ja
        </Tag>
      )

    case KalenderavtaleTilstand.ArbeidsgiverVilEndreTidEllerSted:
      return (
        <Tag size='small' className='notifikasjon_AvtaletilstandLinje' variant='neutral'>
          Du ønsker å endre tid eller sted
        </Tag>
      )

    case KalenderavtaleTilstand.ArbeidsgiverVilAvlyse:
      return (
        <Tag size='small' className='notifikasjon_AvtaletilstandLinje' variant='neutral'>
          Du ønsker å avlyse
        </Tag>
      )

    case KalenderavtaleTilstand.Avlyst:
      return (
        <Tag size='small' className='notifikasjon_AvtaletilstandLinje' variant='info'>
          Avlyst
        </Tag>
      )


    default:
      return null
  }
}
