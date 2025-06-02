import { gql, TypedDocumentNode, useMutation, useQuery } from '@apollo/client';
import { useEffect, useState } from 'react';
import useLocalStorage from './useLocalStorage';
import { MutationNotifikasjonerSistLestArgs, NotifikasjonerSistLestResultat, Query } from '../api/graphql-types';

const MUTATION_NOTIFIKASJONER_SIST_LEST: TypedDocumentNode<
  NotifikasjonerSistLestResultat,
  MutationNotifikasjonerSistLestArgs
> = gql`
  mutation notifikasjonerSistLest($tidspunkt: ISO8601DateTime!) {
    notifikasjonerSistLest(tidspunkt: $tidspunkt) {
      ... on NotifikasjonerSistLest {
        tidspunkt
      }
    }
  }
`;

const QUERY_NOTIFIKASJONER_SIST_LEST: TypedDocumentNode<Pick<Query, 'notifikasjonerSistLest'>> =
gql`
  query notifikasjonerSistLest {
    notifikasjonerSistLest {
      ... on NotifikasjonerSistLest {
        tidspunkt
      }
    }
  }
`;

export const useNotifikasjonerSistLest = () => {
  const { loading, error, data } = useQuery(QUERY_NOTIFIKASJONER_SIST_LEST);
  const [sistLest, setSistLest] = useState<string | undefined>(undefined);
  const [localStorageSistLest, _] = useLocalStorage<
    string | undefined
  >('sist_lest', undefined);
  const [mutationNotifikasjonerSistLest] = useMutation(MUTATION_NOTIFIKASJONER_SIST_LEST);

  useEffect(() => {
    if (loading) {
      return;
    }
    if (error) {
      console.error('Error fetching sist lest:', error);
      return;
    }
    if (data && data.notifikasjonerSistLest.tidspunkt !== null) {
      setSistLest(data.notifikasjonerSistLest.tidspunkt);
    }
    // Dersom sistLest er null, populerer den fra localstorage.
    else if (localStorageSistLest !== undefined) {
      mutationNotifikasjonerSistLest({
        variables: { tidspunkt: localStorageSistLest },
      });
      setSistLest(localStorageSistLest);
      window.localStorage.removeItem('sist_lest');
    }
  }, [loading]);

  return { sistLest, setSistLest, mutationNotifikasjonerSistLest };
};
