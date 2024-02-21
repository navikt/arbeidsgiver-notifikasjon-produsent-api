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
                <header style={{width: "100vw", padding: "2rem", display: "flex", flexDirection: "row", gap: "2rem", alignItems: "baseline"}}>
                    <img alt="shipit squirrel" src={shipit} style={{width: "100px"}}/>
                    <Heading size="large">
                        Notifikasjoner for Arbeidsgiver Testprodusent
                    </Heading>
                </header>
                <main>
                    <div style={{display: "flex", flexDirection: "row", gap: "2rem"}}>
                        <nav className="meny">
                            <ul style={{listStyle: "none"}}>
                                {alleKomponenter.map((key) => (
                                    <li key={key}>
                                        <Button size="small"
                                                variant="tertiary"
                                                onClick={() => setValgtKomponent(komponenter[key])}>{key}</Button>
                                    </li>))}
                            </ul>
                        </nav>
                        {valgtKomponent}
                    </div>
                </main>

            </div>
        </ApolloProvider>
    )
}

export default App
