import {ApolloClient, ApolloProvider, InMemoryCache} from '@apollo/client';
import {Button, Heading} from "@navikt/ds-react";
import "@navikt/ds-css";
import "./App.css"
import React, {ReactNode} from "react";
import {alleKomponenter, komponenter} from "./Komponenter/Komponenter";
import shipit from "./assets/shipit.png"

const client = new ApolloClient({
    uri: '/notifikasjon-produsent-api',
    cache: new InMemoryCache(),
});

function App() {
    const [valgtKomponent, setValgtKomponent] = React.useState<ReactNode>(Object.values(komponenter)[0])
    return (
        <ApolloProvider client={client}>
            <div className="hovedside" style={{display: "flex", flexDirection: "column"}}>
                <header className="header">
                    <img alt="shipit squirrel" src={shipit} style={{width: "100px"}}/>
                    <Heading size="xlarge" level="1">
                        Notifikasjoner for Arbeidsgiver Testprodusent
                    </Heading>
                </header>
                <main className="hovedside">
                    <nav className="meny">
                        <Heading size={"medium"} level="2">Velg query</Heading>
                        <ul>
                            {alleKomponenter.map((key) => (
                                <li key={key}>
                                    <Button size="small"
                                            variant="tertiary"
                                            onClick={() => setValgtKomponent(komponenter[key])}>{key}</Button>
                                </li>))}
                        </ul>
                    </nav>
                    <div className="innhold">
                        {valgtKomponent}
                    </div>
                </main>
            </div>
        </ApolloProvider>
    )
}

export default App
