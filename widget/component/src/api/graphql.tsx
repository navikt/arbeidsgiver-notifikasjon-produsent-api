import {ApolloClient, from, gql, HttpLink, InMemoryCache, TypedDocumentNode,} from '@apollo/client'
import {Query} from "./graphql-types";
import {RetryLink} from "@apollo/client/link/retry";

export const createClient = (uri: string) =>
  new ApolloClient({
    cache: new InMemoryCache(),
    link: from([
      new RetryLink({
        attempts: {
          max: 25,
          retryIf: (error, _operation) => {
            if (error.statusCode === 401) {
              // do not retry 401
              return false;
            }
            return !!error;
          }
        }
      }),
      new HttpLink({uri}),
    ]),
  })

export const HENT_NOTIFIKASJONER: TypedDocumentNode<Pick<Query, "notifikasjoner">> = gql`
  query hentNotifikasjoner {
    notifikasjoner {
        feilAltinn
        feilDigiSyfo
        notifikasjoner {
            __typename
            ... on Beskjed {
                brukerKlikk {
                    id
                    klikketPaa
                }
                virksomhet {
                    navn
                    virksomhetsnummer
                }
                lenke
                tekst
                merkelapp
                opprettetTidspunkt
                sorteringTidspunkt
                id
                sak {
                  tittel
                }
            }
            ... on Oppgave {
                brukerKlikk {
                    id
                    klikketPaa
                }
                virksomhet {
                    navn
                    virksomhetsnummer
                }
                lenke
                tekst
                merkelapp
                opprettetTidspunkt
                sorteringTidspunkt
                paaminnelseTidspunkt
                utgaattTidspunkt
                utfoertTidspunkt
                tilstand
                id
                frist
                sak {
                  tittel
                }
            }
            ... on Kalenderavtale {
              brukerKlikk {
                id
                klikketPaa
              }
              virksomhet {
                navn
                virksomhetsnummer
              }
              lenke
              tekst
              merkelapp
              opprettetTidspunkt
              sorteringTidspunkt
              startTidspunkt
              sluttTidspunkt
              lokasjon {
                adresse
                postnummer
                poststed
              }
              digitalt
              avtaletilstand
              id
              sak {
                tittel
              }
            }
        }
    }
  }
`

export const NOTIFIKASJONER_KLIKKET_PAA = gql`
  mutation NotifikasjonKlikketPaa($id: ID!) {
    notifikasjonKlikketPaa(id: $id) {
      ... on BrukerKlikk {
        id
        klikketPaa
      }
      ... on UgyldigId {
        feilmelding
      }
    }
  }
`
