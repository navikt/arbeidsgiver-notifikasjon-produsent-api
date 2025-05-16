import { FC, ReactElement, ReactNode } from 'react';
import { ChevronRightIcon, ClockIcon } from '@navikt/aksel-icons';
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
import './NotifikasjonListeElement.css';
import {
  BeskjedIkon,
  KalenderavtaleIkon,
  NyOppgaveIkon,
  OppgaveUtfortIkon,
  OppgaveUtgaattIkon,
} from './Ikoner';

interface NotifikasjonListeElementProps {
  notifikasjon: Notifikasjon;
  antall: number;
}

export const NotifikasjonListeElement = ({ ...props }: NotifikasjonListeElementProps) => {
  const notifikasjon = props.notifikasjon;

  switch (notifikasjon.__typename) {
    case 'Beskjed':
      return (
        <NotifikasjonLenke
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
            <NotifikasjonLenke
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
            <NotifikasjonLenke
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
            <NotifikasjonLenke
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={<OppgaveUtgaattIkon title="Utgått oppgave" />}
              tittel={notifikasjon.tekst}
              visningstidspunkt={new Date(notifikasjon.opprettetTidspunkt)}
              statuslinje={
                notifikasjon.frist !== null ? (
                  <StatusIkonMedTekst variant="neutral">
                    Fristen gikk ut{' '}
                    {uformellDatotekst(new Date(notifikasjon.utgaattTidspunkt))}
                  </StatusIkonMedTekst>
                ) : (
                  <Tag size="small" variant="neutral">
                    Utgått{' '}
                    {uformellDatotekst(new Date(notifikasjon.utgaattTidspunkt))}
                  </Tag>
                )
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
      const tidpunktFormatert = kalenderavtaleTidspunkt(notifikasjon);
      switch (avtaletilstand) {
        case KalenderavtaleTilstand.VenterSvarFraArbeidsgiver:
          return (
            <NotifikasjonLenke
              notifikasjon={notifikasjon}
              props={props}
              erTodo={!harPassert}
              ikon={
                harPassert ? (
                  <KalenderavtaleIkon
                    variant="grå"
                    title="Kalenderavtale som har passert."
                  />
                ) : (
                  <KalenderavtaleIkon
                    variant="oransje"
                    title="Kalenderavtale som du må svare på."
                  />
                )
              }
              tittel={notifikasjon.tekst}
              undertittel={tidpunktFormatert}
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
            <NotifikasjonLenke
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant={harPassert ? 'grå' : 'blå'}
                  title="Kalenderavtale som du har svart på."
                />
              }
              tittel={notifikasjon.tekst}
              undertittel={tidpunktFormatert}
              statuslinje={
                <Tag size="small" variant="success">
                  Du har takket ja
                </Tag>
              }
            />
          );
        case KalenderavtaleTilstand.ArbeidsgiverVilEndreTidEllerSted:
          return (
            <NotifikasjonLenke
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant={harPassert ? 'grå' : 'blå'}
                  title="Kalenderavtale som du har svart på."
                />
              }
              tittel={notifikasjon.tekst}
              undertittel={tidpunktFormatert}
              statuslinje={
                <Tag size="small" variant="info">
                  Du ønsker endre tid eller sted
                </Tag>
              }
            />
          );
        case KalenderavtaleTilstand.ArbeidsgiverVilAvlyse:
          return (
            <NotifikasjonLenke
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant={harPassert ? 'grå' : 'blå'}
                  title="Kalenderavtale som du har svart på."
                />
              }
              tittel={notifikasjon.tekst}
              undertittel={tidpunktFormatert}
              statuslinje={
                <Tag size="small" variant="info">
                  Du ønsker å avlyse
                </Tag>
              }
            />
          );
        case KalenderavtaleTilstand.Avlyst:
          return (
            <NotifikasjonLenke
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant="grå"
                  title="Kalenderavtale som er avlyst."
                />
              }
              tittel={notifikasjon.tekst}
              undertittel={tidpunktFormatert}
              statuslinje={
                <Tag size="small" variant="error">
                  Avlyst
                </Tag>
              }
            />
          );
        case KalenderavtaleTilstand.Avholdt:
          return (
            <NotifikasjonLenke
              notifikasjon={notifikasjon}
              props={props}
              erTodo={false}
              ikon={
                <KalenderavtaleIkon
                  variant="grå"
                  title="Kalenderavtale som er avholdt."
                />
              }
              tittel={notifikasjon.tekst}
              undertittel={tidpunktFormatert}
              statuslinje={
                <Tag size="small" variant="success">
                  Avholdt
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

interface NotifikasjonLenkeProps {
  notifikasjon: Notifikasjon;
  props: NotifikasjonListeElementProps;
  erTodo: boolean;
  ikon: ReactElement;
  tittel: string;
  undertittel?: string;
  visningstidspunkt?: Date;
  statuslinje?: ReactNode;
}

const NotifikasjonLenke = ({
                             notifikasjon,
                             erTodo,
                             ikon,
                             tittel,
                             undertittel,
                             visningstidspunkt,
                             statuslinje,
                           }: NotifikasjonLenkeProps) => {
  const { klikketPaa } = notifikasjon.brukerKlikk;

  return (
    <div className="notifikasjonwidget-element">
      <div
        className={`notifikasjonwidget-element-left-border${erTodo ? ' gul' : ''}`}
      />
      {klikketPaa && <BodyShort visuallyHidden>Ikke besøkt</BodyShort>}
      {<BodyShort visuallyHidden>{notifikasjon.__typename}</BodyShort>}
      <div className="notifikasjonwidget-element-innhold">
        {/** VIRKSOMHET **/}
        <BodyShort size="small" spacing>
          {notifikasjon.virksomhet.navn.toUpperCase()}
        </BodyShort>

        {/** SAK **/}
        {notifikasjon.sak && notifikasjon.sak.tittel !== '' && (
          <>
            <BodyShort spacing>
              {klikketPaa ? (
                notifikasjon.sak.tittel
              ) : (
                <strong>{notifikasjon.sak.tittel}</strong>
              )}
            </BodyShort>
            {notifikasjon.sak.tilleggsinformasjon !== '' && (
              <BodyShort size="small" spacing>
                {notifikasjon.sak.tilleggsinformasjon}
              </BodyShort>
            )}
          </>
        )}

        {/** NOTIFIKASJON **/}
        <div className="notifikasjonwidget-element-melding">
          <div>{ikon}</div>
          <div>
            <div>
              <BodyShort
                weight={klikketPaa ? 'regular' : 'semibold'}
                size="small"
                spacing
              >
                {tittel}
              </BodyShort>
              {undertittel !== '' && (
                <BodyShort
                  size="large"
                  weight={klikketPaa ? 'regular' : 'semibold'}
                >
                  {undertittel}
                </BodyShort>
              )}
            </div>
            {visningstidspunkt && (
              <Detail spacing>{sendtDatotekst(visningstidspunkt)}</Detail>
            )}
            <div>{statuslinje}</div>
          </div>
        </div>
      </div>

      <div className="notifikasjonwidget-element-pil">
        <ChevronRightIcon fontSize="1.5rem" aria-hidden />
      </div>
    </div>
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

const kalenderavtaleTidspunkt = (kalenderavtale: Kalenderavtale) => {
  const startTidspunktFormatert = startTidspunktFormat.format(
    new Date(kalenderavtale.startTidspunkt),
  );
  const sluttTidspunktFormatert =
    kalenderavtale.sluttTidspunkt !== undefined &&
    kalenderavtale.sluttTidspunkt !== null
      ? sluttTidsunktFormat.format(new Date(kalenderavtale.sluttTidspunkt))
      : undefined;
  return `${startTidspunktFormatert} ${sluttTidspunktFormatert !== undefined ? `– ${sluttTidspunktFormatert}` : ''}`;
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
      <ClockIcon aria-hidden={true} /> {children}
    </span>
  </Tag>
);
