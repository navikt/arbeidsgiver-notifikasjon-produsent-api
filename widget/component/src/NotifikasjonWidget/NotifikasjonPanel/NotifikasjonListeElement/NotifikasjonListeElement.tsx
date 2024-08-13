import React from 'react'
import { Next as HoyreChevron } from '@navikt/ds-icons'
import { BodyShort, Detail } from '@navikt/ds-react'
import { sendtDatotekst } from '../dato-funksjoner'
import { Kalenderavtale, KalenderavtaleTilstand, Notifikasjon, OppgaveTilstand } from '../../../api/graphql-types'
import { useAmplitude } from '../../../utils/amplitude'
import { StatusLinje } from './StatusLinje'
import './NotifikasjonListeElement.css'
import { BeskjedIkon, KalenderavtaleIkon, NyOppgaveIkon, OppgaveUtfortIkon, OppgaveUtgaattIkon } from './Ikoner'
import { AvtaletilstandLinje } from './AvtaletilstandLinje'

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
      ikon = <BeskjedIkon title='Beskjed' />
      break
    case 'Oppgave':
      const tilstand = props.notifikasjon.tilstand
      const ikoner = {
        NY: <NyOppgaveIkon title='Uløst oppgave' />,
        UTFOERT: <OppgaveUtfortIkon title='Utført oppgave' />,
        UTGAATT: <OppgaveUtgaattIkon title='Utgått oppgave' />
      }
      ikon = ikoner[tilstand ?? OppgaveTilstand.Ny]

      break
    case 'Kalenderavtale':
      const avtaletilstand = props.notifikasjon.avtaletilstand
      const harPassert = new Date(props.notifikasjon.startTidspunkt) < new Date()
      ikon = avtaletilstand === KalenderavtaleTilstand.Avlyst || harPassert ? (
        <KalenderavtaleIkon
          variant='grå'
          title={
            harPassert
              ? 'Kalenderavtale som har passert.'
              : 'Kalenderavtale som er avlyst.'
          }
        />
      ) : avtaletilstand === KalenderavtaleTilstand.VenterSvarFraArbeidsgiver ? (
        <KalenderavtaleIkon
          variant='oransje'
          title={'Kalenderavtale som du må svare på.'}
        />
      ) : (
        <KalenderavtaleIkon
          variant='blå'
          title={'Kalenderavtale som du har svart på.'}
        />
      )
      ikon = <KalenderavtaleIkon title='Kalenderavtale som du må svare på.' variant='oransje' />

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
        <BodyShort>
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
        <BodyShort>
          {notifikasjon.brukerKlikk?.klikketPaa ? (
            notifikasjon.tekst
          ) : (
            <strong>{notifikasjon.tekst}</strong>
          )}
        </BodyShort>
      </>

      break
    case 'Kalenderavtale':
      let kalenderavtaletekst = kalenderavtaleTekst(notifikasjon as Kalenderavtale)
      innhold = <>
        <BodyShort>
          {notifikasjon.brukerKlikk?.klikketPaa ? (
            kalenderavtaletekst
          ) : (
            <strong>{kalenderavtaletekst}</strong>
          )}
        </BodyShort>
      </>

      break
    default:
      console.error(
        // @ts-ignore
        `ukjent notifikasjonstype ${props.notifikasjon.__typename}: ignorerer`
      )
      return null
  }
  const erTodo = (notifikasjon.__typename === 'Oppgave' && notifikasjon.tilstand === OppgaveTilstand.Ny) ||
    (notifikasjon.__typename === 'Kalenderavtale' && notifikasjon.avtaletilstand === KalenderavtaleTilstand.VenterSvarFraArbeidsgiver)
  return (
    <a
      tabIndex={props.erValgt ? 0 : -1}
      href={props.notifikasjon.lenke}
      className={`notifikasjon_liste_element ${erTodo ? 'notifikasjon_liste_element-todo' : ''}`}
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
      <BodyShort className='notifikasjon_liste_element-virksomhet' size='small'>
        {notifikasjon.virksomhet.navn.toUpperCase()}
      </BodyShort>

      {notifikasjon.sak?.tittel ? <BodyShort className='notifikasjon_liste_element-lenkepanel-sakstekst'>
        {notifikasjon.brukerKlikk?.klikketPaa ? (
          notifikasjon.sak?.tittel
        ) : (
          <strong>{notifikasjon.sak?.tittel}</strong>
        )}
      </BodyShort> : null}

      <div className='notifikasjon_liste_element-lenkepanel-ikon'>{ikon}</div>
      <HoyreChevron aria-hidden={true} className='notifikasjon_liste_element-lenkepanel-chevron' />

      {notifikasjon.brukerKlikk?.klikketPaa ? '' : <BodyShort visuallyHidden>Ikke besøkt</BodyShort>}
      <div className='notifikasjon_liste_element-innhold'>
        <div>{innhold}</div>
        {(notifikasjon.__typename === 'Oppgave'
          && notifikasjon.tilstand === OppgaveTilstand.Utfoert
          || notifikasjon.__typename === 'Kalenderavtale') ? null :
          <Detail size='small'>{sendtDatotekst(date)}</Detail>}
        <div>
          <StatusLinje notifikasjon={notifikasjon} />
          <AvtaletilstandLinje notifikasjon={notifikasjon} />
        </div>

      </div>
      <div className='notifikasjon_liste_element-tomt' />
    </a>
  )
}

const startTidspunktFormat = new Intl.DateTimeFormat('no', {
  month: 'long',
  day: 'numeric',
  hour: 'numeric',
  minute: 'numeric'
})

const sluttTidsunktFormat = new Intl.DateTimeFormat('no', {
  hour: 'numeric',
  minute: 'numeric'
})

const kalenderavtaleTekst = (kalenderavtale: Kalenderavtale) => {
  const startTidspunkt = new Date(kalenderavtale.startTidspunkt)
  const sluttTidspunkt = kalenderavtale.sluttTidspunkt === undefined || kalenderavtale.sluttTidspunkt === null ? undefined : new Date(kalenderavtale.sluttTidspunkt)
  const tidspunkt = `${startTidspunktFormat.format(startTidspunkt)} ${
    sluttTidspunkt !== undefined ? `– ${sluttTidsunktFormat.format(sluttTidspunkt)}` : ''
  }`
  return `${kalenderavtale.tekst} ${tidspunkt}`
}
