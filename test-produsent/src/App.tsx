import {ApolloClient, InMemoryCache, ApolloProvider} from '@apollo/client';
import {Box, Button, HStack, List, Page} from "@navikt/ds-react";
import "@navikt/ds-css";
import "./App.css"
import React, {FunctionComponent, ReactNode} from "react";
import {komponenter} from "./Komponenter/Komponenter";


const client = new ApolloClient({
    uri: '/notifikasjon-produsent-api',
    cache: new InMemoryCache(),
});



function App() {
    const [valgtKomponent, setValgtKomponent] = React.useState<ReactNode>(komponenter["Who am I"])
    return (
        <ApolloProvider client={client}>
            <Page className="hovedside" title="Arbeidsgiver Testprodusent">
                <Box as="header" background="surface-neutral-moderate" padding="8">
                    <Page.Block gutters width="2xl">
                        Header
                    </Page.Block>
                </Box>
                <Box
                    as="main"
                    background="surface-alt-3-moderate"
                >
                    <Page.Block gutters className={"body"} width="2xl">
                        <HStack gap="4">
                            <Box as="nav" className="meny">
                                <List>
                                    {Object.keys(komponenter).map((key) => (
                                        <List.Item key={key}>
                                            <Button variant="tertiary" onClick={() => setValgtKomponent(komponenter[key])}>{key}</Button>
                                        </List.Item>))}
                                </List>
                            </Box>
                            <Box background="bg-subtle" className="innhold">
                                {valgtKomponent}
                            </Box>
                        </HStack>
                    </Page.Block>
                </Box>

            </Page>
        </ApolloProvider>
    )
}

export default App
