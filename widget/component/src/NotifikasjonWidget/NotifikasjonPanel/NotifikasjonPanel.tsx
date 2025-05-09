import React, { useEffect, useRef, useState, KeyboardEvent } from 'react';
import { Alert, Heading, Link } from '@navikt/ds-react';
import { NotifikasjonListeElement } from './NotifikasjonListeElement/NotifikasjonListeElement';
import './NotifikasjonPanel.css';
import { Notifikasjon, NotifikasjonerResultat } from '../../api/graphql-types';
import { useMutation } from '@apollo/client';
import { NOTIFIKASJONER_KLIKKET_PAA } from '../../api/graphql';
import { LukkIkon } from './NotifikasjonListeElement/Ikoner';
import { ExpandIcon } from '@navikt/aksel-icons';
import { useAnalytics } from '../../context/AnalyticsProvider';
import { getLimitedUrl } from '../../utils/utils';

interface NotifikasjonsPanelProps {
  erApen: boolean;
  onLukkPanel: () => void;
  notifikasjoner: NotifikasjonerResultat;
}

const NotifikasjonPanel = (
  {
    notifikasjoner: { notifikasjoner, feilAltinn, feilDigiSyfo },
    erApen,
    onLukkPanel,
  }: NotifikasjonsPanelProps,
) => {
  if (notifikasjoner.length === 0) return null;

  const logEvent = useAnalytics();

  const notifikasjonRefs = useRef<(HTMLAnchorElement | null)[]>([]);
  const notifikasjonPanelListeRef = useRef<HTMLUListElement>(null);

  const [valgtNotifikasjonIndex, setValgtNotifikasjonIndex] = useState(0);

  const loggNotifikasjonKlikk = (notifikasjon: Notifikasjon, index: number) => {
    const klikketPaaTidligere = notifikasjon.brukerKlikk.klikketPaa;
    logEvent('notifikasjon-klikk', {
      url: getLimitedUrl(),
      index,
      'merkelapp': notifikasjon.merkelapp,
      'klikket-paa-tidligere': klikketPaaTidligere,
      'destinasjon': notifikasjon.lenke,
    });
  };

  const lukkPanel = () => {
    setValgtNotifikasjonIndex(0);
    onLukkPanel();
  };

  const focusNotifikasjon = () => notifikasjonRefs.current[valgtNotifikasjonIndex]?.focus();

  useEffect(() => {
    if (erApen) {
      notifikasjonPanelListeRef.current?.scrollTo(0, 0);
      setValgtNotifikasjonIndex(0);
      focusNotifikasjon();
    }
  }, [erApen]);

  useEffect(() => {
    if (erApen) {
      focusNotifikasjon();
    }
  }, [erApen, valgtNotifikasjonIndex]);

  const [notifikasjonKlikketPaa] = useMutation(NOTIFIKASJONER_KLIKKET_PAA);

  const handleNotifikasjonKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const maksLengde = notifikasjoner?.length ?? 0;

    logEvent('piltast-navigasjon', { url: getLimitedUrl() });

    const key = e.key;

    switch (key) {
      case 'Enter':
        e.stopPropagation();
        break;
      case 'ArrowDown':
        e.preventDefault();

        setValgtNotifikasjonIndex((prev) =>
          prev < maksLengde - 1 ? prev + 1 : prev,
        );
        break;
      case 'ArrowUp':
        e.preventDefault();

        setValgtNotifikasjonIndex((prev) =>
          prev > 0 ? prev - 1 : prev,
        );
        break;
      case 'Escape':
        e.preventDefault();

        lukkPanel();
        break;
    }
  };

  return (
    <div
      id="notifikasjon_panel"
      className="notifikasjon_panel"
      onKeyDown={handleNotifikasjonKeyDown}
    >
      <div
        id="notifikasjon_panel-header"
        className="notifikasjon_panel-header"
      >
        <div className="notifikasjon_panel-header-title-help">
          <Link href={'https://arbeidsgiver.nav.no/min-side-arbeidsgiver/saksoversikt'}>
            <Heading level="2" size="small">Søk og filtrer på alle saker</Heading><ExpandIcon width={24} height={24} />
          </Link>
        </div>
        <button
          id="notifikasjon_panel-header-xbtn"
          className="notifikasjon_panel-header-xbtn"
          onClick={lukkPanel}
        >
          <LukkIkon />
        </button>
      </div>

      {(feilAltinn || feilDigiSyfo) && (
        <div className="notifikasjon_panel-feilmelding">
          {feilAltinn && (
            <Alert variant="error">
              Vi opplever ustabilitet med Altinn, så du ser kanskje ikke alle notifikasjoner. Prøv igjen senere.
            </Alert>
          )}
          {feilDigiSyfo && (
            <Alert variant="error">
              Vi opplever feil og kan ikke hente eventuelle notifikasjoner for sykemeldte som du skal følge opp. Prøv
              igjen senere.
            </Alert>
          )}
        </div>
      )}

      <ul
        role="feed"
        id="notifikasjon_panel-liste"
        ref={notifikasjonPanelListeRef}
        className="notifikasjon_panel-liste notifikasjon_panel-liste_shadows"
      >
        {notifikasjoner.map((notifikasjon: Notifikasjon, index: number) => (
          <li key={index} role="article">
            <NotifikasjonListeElement
              ref={(el: HTMLAnchorElement) => (notifikasjonRefs.current[index] = el)}
              antall={notifikasjoner.length}
              onKlikketPaaLenke={(klikketPaaNotifikasjon: Notifikasjon) => {
                loggNotifikasjonKlikk(klikketPaaNotifikasjon, index);
                notifikasjonKlikketPaa({ variables: { id: klikketPaaNotifikasjon.id } });
                setValgtNotifikasjonIndex(index);
              }}
              notifikasjon={notifikasjon}
            />
          </li>
        ))}
      </ul>
    </div>
  );
};

export default NotifikasjonPanel;
