import React, {useEffect, useState} from 'react'
import { Alert, Heading, Link } from '@navikt/ds-react';
import {NotifikasjonListeElement} from './NotifikasjonListeElement/NotifikasjonListeElement'
import './NotifikasjonPanel.css'
import {Notifikasjon, NotifikasjonerResultat} from '../../api/graphql-types'
import {useMutation} from '@apollo/client'
import {NOTIFIKASJONER_KLIKKET_PAA} from '../../api/graphql'
import {useAmplitude} from '../../utils/amplitude'
import { LukkIkon } from './NotifikasjonListeElement/Ikoner'
import { ExpandIcon } from '@navikt/aksel-icons';


interface Props {
  erApen: boolean
  onLukkPanel: () => void
  notifikasjoner: NotifikasjonerResultat
}

const NotifikasjonPanel = (
  {
    notifikasjoner: {notifikasjoner, feilAltinn, feilDigiSyfo},
    erApen,
    onLukkPanel
  }: Props
) => {
  if (notifikasjoner.length === 0) {
    return null;
  }

  const {loggNotifikasjonKlikk} = useAmplitude()
  const [valgtNotifikasjon, setValgtNotifikasjon] = useState(notifikasjoner[0])

  const lukkPanel = () => {
    setValgtNotifikasjon(notifikasjoner[0])
    onLukkPanel()
  }

  const focusXButton = () => {
    document.getElementById('notifikasjon_panel-header-xbtn')?.focus()
  }
  const focusNotifikasjon = () => {
    document
      .getElementById('notifikasjon_liste_element-id-' + valgtNotifikasjon.id)
      ?.focus()
  }
  const focusMoreInfo = () => {
    document.getElementById('notifikasjon-informasjon-knapp')?.focus()
  }


  useEffect(() => {
    if (erApen) {
      const containerElement = document.getElementById(
        'notifikasjon_panel-liste'
      )
      containerElement?.scrollTo(0, 0)
      setValgtNotifikasjon(notifikasjoner[0])
      focusNotifikasjon()
    }
  }, [erApen])

  useEffect(() => {
    if (erApen) {
      focusNotifikasjon()
    }
  }, [erApen, valgtNotifikasjon, notifikasjoner])

  const [notifikasjonKlikketPaa] = useMutation(NOTIFIKASJONER_KLIKKET_PAA)

  return (
    <div
      id='notifikasjon_panel'
      className='notifikasjon_panel'
      onKeyDown={({key}) => {
        if (key === 'Escape' || key === 'Esc') {
          lukkPanel()
        }
      }}
    >
      <div
        id='notifikasjon_panel-header'
        className='notifikasjon_panel-header'
      >
        <div className="notifikasjon_panel-header-title-help">
          <Link href={"https://arbeidsgiver.nav.no/min-side-arbeidsgiver/saksoversikt"}>
            <Heading level='2' size='small'>Søk og filtrer på alle saker</Heading><ExpandIcon width={24} height={24}/>
          </Link>
        </div>
        <button
          id='notifikasjon_panel-header-xbtn'
          className='notifikasjon_panel-header-xbtn'
          onClick={() => {
            lukkPanel()
          }}
        >
          <LukkIkon />
        </button>
      </div>

      {(feilAltinn || feilDigiSyfo) ?
        <div className='notifikasjon_panel-feilmelding'>
          {feilAltinn ?
            <Alert variant='error'>
              Vi opplever ustabilitet med Altinn, så du
              ser kanskje ikke alle notifikasjoner.
              Prøv igjen senere.
            </Alert>
            : null}

          {feilDigiSyfo ?
            <Alert variant='error'>
              Vi opplever feil og kan ikke hente
              eventuelle notifikasjoner for sykemeldte
              som du skal følge opp.
              Prøv igjen senere.
            </Alert>
            : null}
        </div>
        : null
      }

      <ul
        role='feed'
        id='notifikasjon_panel-liste'
        className='notifikasjon_panel-liste notifikasjon_panel-liste_shadows'
      >
        {notifikasjoner.map((notifikasjon: Notifikasjon, index: number) => (
          <li key={index} role='article'>
            <NotifikasjonListeElement
              antall={notifikasjoner.length}
              erValgt={notifikasjon === valgtNotifikasjon}
              gåTilForrige={() => {
                const forrigeIndex = Math.max(0, (notifikasjoner.indexOf(notifikasjon)) - 1)
                setValgtNotifikasjon(notifikasjoner[forrigeIndex])
              }}
              gåTilNeste={() => {
                const nesteIndex = Math.min(notifikasjoner.indexOf(notifikasjon) + 1, notifikasjoner.length - 1)
                setValgtNotifikasjon(notifikasjoner[nesteIndex])
              }}
              onKlikketPaaLenke={(klikketPaaNotifikasjon) => {
                // noinspection JSIgnoredPromiseFromCall sentry håndterer unhandled promise rejections
                loggNotifikasjonKlikk(klikketPaaNotifikasjon, notifikasjoner.indexOf(notifikasjon))
                notifikasjonKlikketPaa({variables: {id: klikketPaaNotifikasjon.id}})
                setValgtNotifikasjon(klikketPaaNotifikasjon)
              }}
              notifikasjon={notifikasjon}
              onTabEvent={(shiftKey) => {
                if (shiftKey) {
                  focusXButton()
                } else {
                  focusMoreInfo()
                }
              }}
            />
          </li>
        ))}
      </ul>
    </div>
  )
}

export default NotifikasjonPanel
