import React, { FC, ReactElement, ReactNode } from 'react';
import { Next as HoyreChevron, StopWatch } from '@navikt/ds-icons';
import { BodyShort, Detail, Tag } from '@navikt/ds-react';
import {
  formatterDato,
  sendtDatotekst,
  uformellDatotekst,
} from '../dato-funksjoner';
import {
  Kalenderavtale,
  KalenderavtaleTilstand,
  Notifikasjon,
  Oppgave,
  OppgaveTilstand,
} from '../../../api/graphql-types';
import { useAmplitude } from '../../../utils/amplitude';
import './NotifikasjonListeElement.css';
import {
  BeskjedIkon,
  KalenderavtaleIkon,
  NyOppgaveIkon,
  OppgaveUtfortIkon,
  OppgaveUtgaattIkon,
} from './Ikoner';

interface Props {
  notifikasjon: Notifikasjon;
  antall: number;
  onKlikketPaaLenke: (notifikasjon: Notifikasjon) => void;
  onTabEvent?: (shiftKey: boolean) => void;
  gåTilForrige: () => void;
  gåTilNeste: () => void;
  erValgt: boolean;
}

export const NotifikasjonListeElement = (props: Props) => {
  const notifikasjon = props.notifikasjon;

  switch (notifikasjon.__typename) {
    case 'Beskjed':
      return (
        <NotifikasjonBeskjed
          notifikasjon={notifikasjon}
          props={props}
          erTodo={false}
          ikon={<BeskjedIkon title="Beskjed" />}
          tittel={notifikasjon.tekst}
          visningstidspunkt={new Date(notifikasjon.opprettetTidspunkt)}
        />
      );

    case 'Oppgave':
      const tilstand = notifikasjon.tilstand;
      switch (tilstand) {
        case OppgaveTilstand.Ny:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={true}
              ikon={<NyOppgaveIkon title="Uløst oppgave" />}
              tittel={notifikasjon.tekst}
              visningstidspunkt={new Date(notifikasjon.opprettetTidspunkt)}
              statuslinje={<StatuslinjeOppgaveNy notifikasjon={notifikasjon} />}
            />
          );
        case OppgaveTilstand.Utfoert:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={<OppgaveUtfortIkon title="Utført oppgave" />}
              tittel={notifikasjon.tekst}
              statuslinje={
                <Tag size="small" variant="success">
                  Utført{' '}
                  {notifikasjon.utfoertTidspunkt
                    ? uformellDatotekst(new Date(notifikasjon.utfoertTidspunkt))
                    : null}
                </Tag>
              }
            />
          );
        case OppgaveTilstand.Utgaatt:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={<OppgaveUtgaattIkon title="Utgått oppgave" />}
              tittel={notifikasjon.tekst}
              visningstidspunkt={new Date(notifikasjon.opprettetTidspunkt)}
              statuslinje={
                notifikasjon.frist !== null ?
                  <StatusIkonMedTekst variant="neutral">
                    Fristen gikk ut{' '}
                    {uformellDatotekst(new Date(notifikasjon.utgaattTidspunkt))}
                  </StatusIkonMedTekst>
                  :
                  <Tag size="small" variant="neutral">
                    Utgått {uformellDatotekst(new Date(notifikasjon.utgaattTidspunkt))}
                  </Tag>
              }
            />
          );
        default:
          console.error(`ukjent oppgavetilstand ${tilstand}: ignorerer`);
          return null;
      }
    case 'Kalenderavtale':
      const avtaletilstand = notifikasjon.avtaletilstand;
      const harPassert = new Date(notifikasjon.startTidspunkt) < new Date();

      switch (avtaletilstand) {
        case KalenderavtaleTilstand.VenterSvarFraArbeidsgiver:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={!harPassert}
              ikon={
                harPassert ? (
                  <KalenderavtaleIkon
                    variant="grå"
                    title={'Kalenderavtale som har passert.'}
                  />
                ) : (
                  <KalenderavtaleIkon
                    variant="oransje"
                    title={'Kalenderavtale som du må svare på.'}
                  />
                )
              }
              tittel={kalenderavtaleTekst(notifikasjon)}
              statuslinje={
                harPassert ? undefined : (
                  <Tag size="small" variant="warning">
                    Svar på invitasjonen
                  </Tag>
                )
              }
            />
          );
        case KalenderavtaleTilstand.ArbeidsgiverHarGodtatt:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant={harPassert ? 'grå' : 'blå'}
                  title={'Kalenderavtale som du har svart på.'}
                />
              }
              tittel={kalenderavtaleTekst(notifikasjon)}
              statuslinje={
                <Tag size="small" variant="success">
                  Du har takket ja
                </Tag>
              }
            />
          );
        case KalenderavtaleTilstand.ArbeidsgiverVilEndreTidEllerSted:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant={harPassert ? 'grå' : 'blå'}
                  title={'Kalenderavtale som du har svart på.'}
                />
              }
              tittel={kalenderavtaleTekst(notifikasjon)}
              statuslinje={
                <Tag size="small" variant="neutral">
                  Du ønsker endre tid eller sted
                </Tag>
              }
            />
          );
        case KalenderavtaleTilstand.ArbeidsgiverVilAvlyse:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant={harPassert ? 'grå' : 'blå'}
                  title={'Kalenderavtale som du har svart på.'}
                />
              }
              tittel={kalenderavtaleTekst(notifikasjon)}
              statuslinje={
                <Tag size="small" variant="neutral">
                  Du ønsker å avlyse
                </Tag>
              }
            />
          );
        case KalenderavtaleTilstand.Avlyst:
          return (
            <NotifikasjonBeskjed
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant="grå"
                  title="Kalenderavtale som er avlyst."
                />
              }
              tittel={kalenderavtaleTekst(notifikasjon)}
              statuslinje={
                <Tag size="small" variant="info">
                  Avlyst
                </Tag>
              }
            />
          );
        default:
          console.error(`ukjent avtaletilstand ${avtaletilstand}: ignorerer`);
          return null;
      }
    default:
      console.error(
        `ukjent notifikasjonstype ${notifikasjon.__typename}: ignorerer`,
      );
      return null;
  }
};

const NotifikasjonBeskjed = ({
                               notifikasjon,
                               props,
                               erTodo,
                               ikon,
                               tittel,
                               visningstidspunkt,
                               statuslinje,
                             }: {
  notifikasjon: Notifikasjon
  props: Props
  erTodo: boolean
  ikon: ReactElement
  tittel: string
  visningstidspunkt?: Date
  statuslinje?: ReactElement
}) => {
  const { loggPilTastNavigasjon } = useAmplitude();

  return (
    <a
      tabIndex={props.erValgt ? 0 : -1}
      href={props.notifikasjon.lenke}
      className={`notifikasjon_liste_element ${erTodo ? 'notifikasjon_liste_element-todo' : ''}`}
      id={'notifikasjon_liste_element-id-' + props.notifikasjon.id}
      onKeyDown={(event) => {
        loggPilTastNavigasjon();
        if (event.key === 'Tab') {
          props.onTabEvent?.(event.shiftKey);
          event.preventDefault();
        }
        if (event.key === 'ArrowUp' || event.key === 'Up') {
          props.gåTilForrige();
        }
        if (event.key === 'ArrowDown' || event.key === 'Down') {
          props.gåTilNeste();
        }
      }}
      onClick={() => {
        props.onKlikketPaaLenke(notifikasjon);
      }}
    >
      <BodyShort className="notifikasjon_liste_element-virksomhet" size="small">
        {notifikasjon.virksomhet.navn.toUpperCase()}
      </BodyShort>

      {notifikasjon.sak?.tittel ? (<>
          <BodyShort className="notifikasjon_liste_element-lenkepanel-sakstekst">
            {notifikasjon.brukerKlikk?.klikketPaa ? (
              notifikasjon.sak?.tittel
            ) : (
              <strong>{notifikasjon.sak?.tittel}</strong>
            )}
          </BodyShort>
          {notifikasjon.sak.tilleggsinformasjon ?
            <BodyShort
              size='small'
            className="notifikasjon_liste_element-lenkepanel-tilleggsinformasjon"
            > {notifikasjon.sak.tilleggsinformasjon}
            </BodyShort> : null}
        </>
      ) : null}

      <div className="notifikasjon_liste_element-lenkepanel-ikon">{ikon}</div>
      <HoyreChevron
        aria-hidden={true}
        className="notifikasjon_liste_element-lenkepanel-chevron"
      />

      {notifikasjon.brukerKlikk?.klikketPaa ? (
        ''
      ) : (
        <BodyShort visuallyHidden>Ikke besøkt</BodyShort>
      )}
      <div className="notifikasjon_liste_element-innhold">
        <>
          <BodyShort>
            {notifikasjon.brukerKlikk?.klikketPaa ? (
              tittel
            ) : (
              <strong>{tittel}</strong>
            )}
          </BodyShort>
        </>
        {visningstidspunkt === undefined ? null : (
          <Detail size="small">{sendtDatotekst(visningstidspunkt)}</Detail>
        )}
        <div>{statuslinje}</div>
      </div>
      <div className="notifikasjon_liste_element-tomt" />
    </a>
  );
};

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
  const startTidspunkt = new Date(kalenderavtale.startTidspunkt);
  const sluttTidspunkt =
    kalenderavtale.sluttTidspunkt === undefined ||
    kalenderavtale.sluttTidspunkt === null
      ? undefined
      : new Date(kalenderavtale.sluttTidspunkt);
  const tidspunkt = `${startTidspunktFormat.format(startTidspunkt)} ${
    sluttTidspunkt !== undefined
      ? `– ${sluttTidsunktFormat.format(sluttTidspunkt)}`
      : ''
  }`;
  return `${kalenderavtale.tekst} ${tidspunkt}`;
};

const StatuslinjeOppgaveNy = ({ notifikasjon }: { notifikasjon: Oppgave }) => {
  if (!notifikasjon.frist && !notifikasjon.paaminnelseTidspunkt) {
    return null;
  } else {
    let innhold;
    if (!notifikasjon.frist && notifikasjon.paaminnelseTidspunkt) {
      innhold = <>Påminnelse</>;
    } else if (notifikasjon.frist && !notifikasjon.paaminnelseTidspunkt) {
      innhold = <>Frist {formatterDato(new Date(notifikasjon.frist))}</>;
    } else {
      innhold = (
        <>
          Påminnelse &ndash; Frist {formatterDato(new Date(notifikasjon.frist))}
        </>
      );
    }
    return (
      <StatusIkonMedTekst variant="warning"> {innhold} </StatusIkonMedTekst>
    );
  }
};

const StatusIkonMedTekst: FC<{
  children: ReactNode
  variant: 'success' | 'neutral' | 'warning'
}> = ({ variant, children }) => (
  <Tag size="small" variant={variant}>
    <span>
      <StopWatch aria-hidden={true} /> {children}
    </span>
  </Tag>
);
