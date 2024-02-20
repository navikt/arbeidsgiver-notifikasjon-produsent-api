import { ApolloClient, InMemoryCache, ApolloProvider } from '@apollo/client';
import {Box, Heading, Page} from "@navikt/ds-react";
import "@navikt/ds-css";
import "./App.css"


const client = new ApolloClient({
    uri: '/notifikasjon-produsent-api',
    cache: new InMemoryCache(),
});

const Header = () => {
    return  <Heading className="header" level="1" size="xlarge" spacing>
                Arbeidsgiver Testprodusent
            </Heading>

}

function App() {
  return (
      <ApolloProvider client={client}>
        <Page className="hovedside" title="Arbeidsgiver Testprodusent">
            <Header/>

        </Page>
      </ApolloProvider>
  )
}

export default App
