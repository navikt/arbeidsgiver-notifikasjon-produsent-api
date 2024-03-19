import {gql, useMutation} from "@apollo/client";
import React, {useContext, useEffect, useState} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {GrupperingsidContext} from "../App.tsx";
import cssClasses from "./KalenderAvtaleMedEksternVarsling.module.css";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import {Button, Textarea} from "@navikt/ds-react";

const NY_BESKJED = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $lenke: String!
        $tekst: String!
        $eksternId: String!
        $merkelapp: String!

    ) {
        nyBeskjed(
            nyBeskjed: {
                mottakere: [{
                    altinn: {
                        serviceCode: "4936"
                        serviceEdition: "1"
                    }
                }]
                                notifikasjon: {
                    merkelapp: $merkelapp 
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
            ... on NyBeskjedVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

export const NyBeskjed: React.FunctionComponent = () => {
    const [nyBeskjed, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nyBeskjed">>(NY_BESKJED)

    const grupperingsid = useContext(GrupperingsidContext)

    const [variables, setVariables] = useState({
        grupperingsid: grupperingsid,
        merkelapp: "fager",
        virksomhetsnummer: "910825526",
        lenke: "https://foo.bar",
        tekst: "Dette er en ny beskjed",
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
            {print(NY_BESKJED)}
        </SyntaxHighlighter>
        <Textarea
            style={{fontSize: "12px", lineHeight: "12px"}}
            label="Variabler"
            value={JSON.stringify(variables, null, 2)}
            onChange={(e) => setVariables(JSON.parse(e.target.value))}
        />
        <Button variant="primary"
                onClick={() => nyBeskjed({variables})}>Opprett en ny beskjed</Button>

        {loading && <p>Laster...</p>}
        {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}
