import React from 'react'
import {Next as HoyreChevron} from '@navikt/ds-icons'
import {BodyShort, Detail} from '@navikt/ds-react'
import {sendtDatotekst} from '../dato-funksjoner'
import {Kalenderavtale, KalenderavtaleTilstand, Notifikasjon, OppgaveTilstand} from '../../../api/graphql-types'
import {useAmplitude} from '../../../utils/amplitude'
import {StatusLinje} from './StatusLinje'
import './NotifikasjonListeElement.css'
import {BeskjedIkon, KalenderavtaleIkon, OppgaveIkkeNyIkon, OppgaveIkon} from './Ikoner'
import {AvtaletilstandLinje} from "./AvtaletilstandLinje";

interface Props {
  notifikasjon: Notifikasjon
  antall: number
  onKlikketPaaLenke: (notifikasjon: Notifikasjon) => void
  onTabEvent?: (shiftKey: boolean) => void
  gåTilForrige: () => void
  gåTilNeste: () => void
  erValgt: boolean
}

export const NotifikasjonListeElement = (props: Props) => {
  const { loggPilTastNavigasjon } = useAmplitude()
  const notifikasjon = props.notifikasjon

  const date = new Date(notifikasjon.opprettetTidspunkt)

  let ikon
  switch (props.notifikasjon.__typename) {
    case 'Beskjed':
      ikon = <BeskjedIkon />
      break
    case 'Oppgave':
      ikon =
        props.notifikasjon.tilstand === OppgaveTilstand.Ny ? (
          <OppgaveIkon/>
        ) : (
          <OppgaveIkkeNyIkon />
        )

      break
    case 'Kalenderavtale':
      ikon = <KalenderavtaleIkon/>
      if (props.notifikasjon.avtaletilstand === KalenderavtaleTilstand.Avlyst || new Date(props.notifikasjon.startTidspunkt) < new Date()) {
        ikon = <KalenderavtaleIkon variant='neutral'/>
      }

      break
    default:
      console.error(
        // @ts-ignore
        `ukjent notifikasjonstype ${props.notifikasjon.__typename}: ignorerer`
      )
      return null
  }

  let innhold
  switch (props.notifikasjon.__typename) {
    case 'Beskjed':
      innhold = <>
        <BodyShort size="small">
          {notifikasjon.brukerKlikk?.klikketPaa ? (
            notifikasjon.tekst
          ) : (
            <strong>{notifikasjon.tekst}</strong>
          )}
        </BodyShort>
      </>
      break
    case 'Oppgave':
      innhold = <>
        <BodyShort size="small">
          {notifikasjon.brukerKlikk?.klikketPaa ? (
            notifikasjon.tekst
          ) : (
            <strong>{notifikasjon.tekst}</strong>
          )}
        </BodyShort>
        <StatusLinje notifikasjon={notifikasjon} />
      </>

      break
    case 'Kalenderavtale':
      let kalenderavtaletekst = kalenderavtaleTekst(notifikasjon as Kalenderavtale)
      innhold = <>
        <BodyShort size="small">
          {notifikasjon.brukerKlikk?.klikketPaa ? (
            kalenderavtaletekst
          ) : (
            <strong>{kalenderavtaletekst}</strong>
          )}
        </BodyShort>
        <AvtaletilstandLinje notifikasjon={notifikasjon} />
      </>

      break
    default:
      console.error(
        // @ts-ignore
        `ukjent notifikasjonstype ${props.notifikasjon.__typename}: ignorerer`
      )
      return null
  }

  return (
    <a
      tabIndex={props.erValgt ? 0 : -1}
      href={props.notifikasjon.lenke}
      className='notifikasjon_liste_element'
      id={'notifikasjon_liste_element-id-' + props.notifikasjon.id}
      onKeyDown={(event) => {
        loggPilTastNavigasjon()
        if (event.key === 'Tab') {
          props.onTabEvent?.(event.shiftKey)
          event.preventDefault()
        }
        if (event.key === 'ArrowUp' || event.key === 'Up') {
          props.gåTilForrige()
        }
        if (event.key === 'ArrowDown' || event.key === 'Down') {
          props.gåTilNeste()
        }
      }}
      onClick={() => {
        props.onKlikketPaaLenke(notifikasjon)
      }}
    >
      <div className='notifikasjon_liste_element-lenkepanel'>
        <div className='notifikasjon_liste_element-lenkepanel-ikon'>{ikon}</div>
        <div className='notifikasjon_liste_element-lenkepanel-innhold'>{innhold}</div>
        <div className='notifikasjon_liste_element-lenkepanel-chevron'>
          <HoyreChevron aria-hidden={true} />
        </div>

        <div
          aria-label={notifikasjon.brukerKlikk?.klikketPaa ? '' : 'ikke besøkt'}
        />
      </div>

      <div className='notifikasjon_liste_element-metadata'>
        <Detail className='notifikasjon_liste_element-metadata-dato' size='small'>
          {sendtDatotekst(date)}
        </Detail>
      </div>
      {notifikasjon.sak?.tittel ? <BodyShort>
        {notifikasjon.brukerKlikk?.klikketPaa ? (
          notifikasjon.sak?.tittel
        ) : (
          <strong>{notifikasjon.sak?.tittel}</strong>
        )}
      </BodyShort> : null}
      <BodyShort className='notifikasjon_liste_element-virksomhetsnavn' size='small'>
        {notifikasjon.virksomhet.navn.toUpperCase()}
      </BodyShort>


    </a>
  )
}

const startTidspunktFormat = new Intl.DateTimeFormat('no', {
  month: 'long',
  day: 'numeric',
  hour: 'numeric',
  minute: 'numeric',
});

const sluttTidsunktFormat = new Intl.DateTimeFormat('no', {
  hour: 'numeric',
  minute: 'numeric',
});

const kalenderavtaleTekst = (kalenderavtale: Kalenderavtale) => {
  const startTidspunkt = new Date(kalenderavtale.startTidspunkt)
  const sluttTidspunkt = kalenderavtale.sluttTidspunkt === undefined || kalenderavtale.sluttTidspunkt === null ? undefined : new Date(kalenderavtale.sluttTidspunkt)
  const tidspunkt = `${startTidspunktFormat.format(startTidspunkt)} ${
    sluttTidspunkt !== undefined ? `– ${sluttTidsunktFormat.format(sluttTidspunkt)}` : ''
  }`
  return `${kalenderavtale.tekst} ${tidspunkt}`
}
