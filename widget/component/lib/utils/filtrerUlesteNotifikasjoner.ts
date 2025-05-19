import { Notifikasjon, OppgaveTilstand } from '../api/graphql-types';

export const filtrerUlesteNotifikasjoner = (
  sistLest: string | undefined,
  notifikasjoner: Notifikasjon[]
): Notifikasjon[] => {
  if (sistLest === undefined) return notifikasjoner;

  const sistLestTid = Date.parse(sistLest);

  return notifikasjoner.filter((notifikasjon) => {
    // Hvis notifikasjonen er en oppgave som ikke lenger er i "Ny" tilstand,
    // skal den ikke telles som ulest
    if (
      notifikasjon.__typename === 'Oppgave' &&
      notifikasjon.tilstand !== OppgaveTilstand.Ny
    ) {
      return false;
    }
    return Date.parse(notifikasjon.sorteringTidspunkt) > sistLestTid;
  });
};
