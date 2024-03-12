import {gql, useMutation} from "@apollo/client";
import {print} from "graphql/language";
import React, {useContext, useEffect, useState} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {Button, Textarea} from "@navikt/ds-react";
import cssClasses from "./KalenderAvtaleMedEksternVarsling.module.css";
import {Prism as SyntaxHighlighter} from 'react-syntax-highlighter';
import {darcula} from 'react-syntax-highlighter/dist/esm/styles/prism';
import {GrupperingsidContext} from "../App.tsx";

const NY_OPPGAVE = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $lenke: String!
        $tekst: String!
        $frist: ISO8601Date
        $eksternId: String!
    ) {
        nyOppgave(
            nyOppgave: {
                mottakere: [{
                    altinn: {
                        serviceCode: "4936"
                        serviceEdition: "1"
                    }
                }]
                frist: $frist
                notifikasjon: {
                    merkelapp: "fager"
                    lenke: $lenke
                    tekst: $tekst
                }
                metadata: {
                    grupperingsid: $grupperingsid
                    virksomhetsnummer: $virksomhetsnummer
                    eksternId: $eksternId
                }
            }
        ) {
            __typename
            ... on NyOppgaveVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }

`

export const NyOppgave: React.FunctionComponent = () => {
    const [nyOppgave, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nyOppgave">>(NY_OPPGAVE)

    const grupperingsid = useContext(GrupperingsidContext)

    const [variables, setVariables] = useState({
        frist: "2022-12-24T23:59:59Z",
        merkelapp: "fager",
        lenke: "",
        tekst: "Dette er en oppgave",
        grupperingsid: grupperingsid,
        virksomhetsnummer: "910825526",
        eksternId: "123",
    });

    useEffect(() => {
        setVariables({
            ...variables,
            grupperingsid: grupperingsid,
        })
    }, [grupperingsid]);

    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_OPPGAVE)}
        </SyntaxHighlighter>
        <Textarea
            style={{fontSize: "12px", lineHeight: "12px"}}
            label="Variabler"
            value={JSON.stringify(variables, null, 2)}
            onChange={(e) => setVariables(JSON.parse(e.target.value))}
        />
        <Button variant="primary"
                onClick={() => nyOppgave({variables})}>Opprett en ny oppgave</Button>

        {loading && <p>Laster...</p>}
        {error &&
            <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}