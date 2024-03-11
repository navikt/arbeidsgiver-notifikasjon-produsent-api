import {gql, useMutation} from "@apollo/client";
import React, {useState} from "react";
import {Mutation} from "../api/graphql-types.ts";
import cssClasses from "./KalenderAvtaleMedEksternVarsling.module.css";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import {Button, Textarea} from "@navikt/ds-react";


const NY_SAKSTATUS = gql`
    mutation (
        $id: ID!
        $nyStatus: SaksStatus!
        $nyLenkeTilSak: String
        $overstyrStatustekstMed: String
        $hardDelete: HardDeleteUpdateInput
        $tidspunkt: ISO8601DateTime
    ) {
        nyStatusSak(
            id: $id
            nyStatus: $nyStatus
            nyLenkeTilSak: $nyLenkeTilSak
            overstyrStatustekstMed: $overstyrStatustekstMed
            hardDelete: $hardDelete
            tidspunkt: $tidspunkt
        ) {
            __typename
            ... on NyStatusSakVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

export const NySakstatus: React.FunctionComponent = () => {
    const [nySakstatus, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nyStatusSak">>(NY_SAKSTATUS)


    const [variables, setVariables] = useState({
        id: "123",
        nyStatus: "MOTTATT",
        nyLenkeTilSak: "https://nav.no",
        overstyrStatustekstMed: "Dette er en overstyring",
        tidspunkt: "2025-08-11T10:00:00Z"
    })


    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_SAKSTATUS)}
        </SyntaxHighlighter>
        <Textarea
            style={{fontSize: "12px", lineHeight: "12px"}}
            label="Variabler"
            value={JSON.stringify(variables, null, 2)}
            onChange={(e) => setVariables(JSON.parse(e.target.value))}
        />
        <Button variant="primary"
                onClick={() => nySakstatus({variables})}>Opprett en ny sakstatus</Button>

        {loading && <p>Laster...</p>}
        {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}