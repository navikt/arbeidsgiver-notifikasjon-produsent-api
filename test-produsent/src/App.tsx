import {ApolloClient, ApolloProvider, InMemoryCache} from '@apollo/client';
import {Button, Heading, TextField} from "@navikt/ds-react";
import "@navikt/ds-css";
import "./App.css"
import React, {ReactNode} from "react";
import {alleKomponenter, komponenter} from "./Komponenter/Komponenter";
import shipit from "./assets/shipit.png"

const client = new ApolloClient({
    uri: '/notifikasjon-produsent-api',
    cache: new InMemoryCache(),
});

export const GrupperingsidContext = React.createContext("")

function App() {
    const [valgtKomponent, setValgtKomponent] = React.useState<ReactNode>(Object.values(komponenter)[0])

    const [grupperingsid, setGrupperingsid] = React.useState<string>(() => crypto.randomUUID())


    const handleGrupperingsid = (e: React.ChangeEvent<HTMLInputElement>) => {
        setGrupperingsid(e.target.value)
    }

    return (
        <ApolloProvider client={client}>
            <GrupperingsidContext.Provider value={grupperingsid}>
                <div className="body" style={{display: "flex", flexDirection: "column"}}>
                    <header className="header">
                        <img alt="shipit squirrel" src={shipit} style={{width: "100px"}}/>
                        <Heading size="xlarge" level="1">
                            Notifikasjoner for Arbeidsgiver Testprodusent
                        </Heading>
                        <div style={{display: "flex", gap: "4px", alignItems:"flex-end"}}>
                            <div className="grupperingsid">
                                <TextField
                                    label="Grupperingsid"
                                    type="text"
                                    value={grupperingsid}
                                    style={{width: "22rem"}}
                                    onChange={handleGrupperingsid}/>
                            </div>
                            <Button variant="secondary"
                                    style={{height:"48px"}}
                                    onClick={() => setGrupperingsid(crypto.randomUUID())}>Generer</Button>
                        </div>
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
            </GrupperingsidContext.Provider>
        </ApolloProvider>
    )
}

export default App
