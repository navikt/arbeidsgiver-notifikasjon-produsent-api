import {ApolloClient, InMemoryCache, ApolloProvider} from '@apollo/client';
import {Button, Heading} from "@navikt/ds-react";
import "@navikt/ds-css";
import "./App.css"
import React, {ReactNode} from "react";
import {komponenter} from "./Komponenter/Komponenter";


const client = new ApolloClient({
    uri: '/notifikasjon-produsent-api',
    cache: new InMemoryCache(),
});


function App() {
    const [valgtKomponent, setValgtKomponent] = React.useState<ReactNode>(komponenter["Who am I"])
    return (
        <ApolloProvider client={client}>
            <div className="body">
                <div className="header">
                    <Heading size="xlarge" level="1">Arbeidsgiver Testprodusent</Heading>
                </div>
                <div className="hovedside">
                    <div className="meny">
                        <Heading size={"medium"} level="2">Velg query</Heading>
                        <ul>
                            {Object.keys(komponenter).map((key) => (
                                <li key={key}>
                                    <Button variant="tertiary"
                                            onClick={() => setValgtKomponent(komponenter[key])}>{key}</Button>
                                </li>))}
                        </ul>
                    </div>
                    <div className="innhold">
                        {valgtKomponent}
                    </div>

                </div>

            </div>
        </ApolloProvider>
    )
}

export default App
