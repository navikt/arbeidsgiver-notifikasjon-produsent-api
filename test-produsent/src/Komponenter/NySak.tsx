import {gql, useMutation} from "@apollo/client";
import {print} from "graphql/language";
import React, {useState} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {Button, Textarea} from "@navikt/ds-react";
import cssClasses from "./KalenderAvtaleMedEksternVarsling.module.css";
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { darcula } from 'react-syntax-highlighter/dist/esm/styles/prism';

const NY_SAK = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $lenke: String!
        $tittel: String!
        $initiellStatus: SaksStatus!

    ) {
        nySak(
            mottakere: [{
                altinn: {
                    serviceCode: "4936"
                    serviceEdition: "1"
                }
            }]
            virksomhetsnummer: $virksomhetsnummer,
            grupperingsid: $grupperingsid
            lenke: $lenke
            tittel: $tittel
            merkelapp: "fager",
            initiellStatus: $initiellStatus
        ) {
            __typename
            ... on NySakVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

export const NySak: React.FunctionComponent = () => {
    const [nySak, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nySak">>(NY_SAK)


    const [variables, setVariables] = useState({
        grupperingsid: "8e3ffcd8-d547-4cfb-abc4-f923dd1c56de",
        virksomhetsnummer: "910825526",
        eksternId: "123",
        lenke: "https://foo.bar",
        tittel: "Dette er en ny sak",
        initiellStatus: "MOTTATT"
    });
    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_SAK)}
        </SyntaxHighlighter>
        <Textarea
            style={{fontSize: "12px", lineHeight: "12px"}}
            label="Variabler"
            value={JSON.stringify(variables, null, 2)}
            onChange={(e) => setVariables(JSON.parse(e.target.value))}
        />
        <Button variant="primary"
                onClick={() => nySak({variables})}>Opprett en ny sak</Button>

        {loading && <p>Laster...</p>}
        {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}