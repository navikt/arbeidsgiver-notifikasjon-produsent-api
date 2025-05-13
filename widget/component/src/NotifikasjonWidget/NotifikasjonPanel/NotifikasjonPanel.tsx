import { useEffect, useRef, useState, KeyboardEvent } from 'react';
import { Alert, BodyShort, Button, Dropdown, Link } from '@navikt/ds-react';
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
  togglePanel: () => void;
  notifikasjoner: NotifikasjonerResultat;
}

const NotifikasjonPanel = (
  {
    notifikasjoner: { notifikasjoner, feilAltinn, feilDigiSyfo },
    erApen,
    togglePanel,
  }: NotifikasjonsPanelProps,
) => {
  // if (notifikasjoner.length === 0) return null;

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
    togglePanel();
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
    <Dropdown.Menu onKeyDown={handleNotifikasjonKeyDown} className='notifikasjon-panel-liste'>
      <Dropdown.Menu.GroupedList>
        <Dropdown.Menu.GroupedList.Heading>
          <div className="dropdown-menu-heading-container">
            <BodyShort as="span">
              <Link href="https://arbeidsgiver.nav.no/min-side-arbeidsgiver/saksoversikt">
                Søk og filtrer på alle saker<ExpandIcon width={24} height={24} />
              </Link>
            </BodyShort>

            <Button variant="tertiary" onClick={lukkPanel} icon={<LukkIkon />} />
          </div>
          {
            (feilAltinn || feilDigiSyfo) && (
              <>
                {feilAltinn && (
                  <Alert variant="error">
                    Vi opplever ustabilitet med Altinn, så du ser kanskje ikke alle notifikasjoner. Prøv igjen senere.
                  </Alert>
                )}
                {feilDigiSyfo && (
                  <Alert variant="error">
                    Vi opplever feil og kan ikke hente eventuelle notifikasjoner for sykemeldte som du skal følge opp.
                    Prøv igjen senere.
                  </Alert>
                )}
              </>
            )
          }
        </Dropdown.Menu.GroupedList.Heading>
        <Dropdown.Menu.Divider style={{ margin: 0 }} />

        {notifikasjoner.map((notifikasjon: Notifikasjon, index: number) => (
          <Dropdown.Menu.GroupedList.Item
            key={index}
            as={Link}
            href={notifikasjon.lenke}
            value={notifikasjon.tekst}
            style={{ padding: 0, color: 'unset', textDecoration: 'none' }}
            ref={(el: HTMLAnchorElement) => (notifikasjonRefs.current[index] = el)}
            onClick={() => {
              loggNotifikasjonKlikk(notifikasjon, index);
              notifikasjonKlikketPaa({ variables: { id: notifikasjon.id } });
              setValgtNotifikasjonIndex(index);
            }}
          >
            <NotifikasjonListeElement
              antall={notifikasjoner.length}
              notifikasjon={notifikasjon}
            />
          </Dropdown.Menu.GroupedList.Item>
        ))}
      </Dropdown.Menu.GroupedList>
    </Dropdown.Menu>
  );
};

export default NotifikasjonPanel;
