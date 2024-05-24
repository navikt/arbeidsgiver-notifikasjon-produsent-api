import {gql, useMutation} from "@apollo/client";
import {Mutation} from "../api/graphql-types.ts";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import React from "react";
import cssClasses from "./KalenderAvtale.module.css";
import {Button, TextField} from "@navikt/ds-react";


const NESTE_STEG_SAK = gql`
    mutation (
        $sakId: ID!
        $nesteSteg: String
        $idempotencyKey: String
    ) {
        nesteStegSak(nesteSteg: $nesteSteg, id: $sakId, idempotencyKey: $idempotencyKey) {
            ... on NesteStegSakVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }`

const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s

export const NesteStegSak = () => {
    const sakIdRef = React.useRef<HTMLInputElement>(null)
    const nesteStegRef = React.useRef<HTMLInputElement>(null)
    const idempotencyKeyRef = React.useRef<HTMLInputElement>(null)

    const [nesteStegSak, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nesteStegSak">>(NESTE_STEG_SAK)

    const handleSend = () => {
        nesteStegSak({
            variables: {
                sakId: sakIdRef.current?.value ?? "",
                nesteSteg: nullIfEmpty(nesteStegRef.current?.value ?? ""),
                idempotencyKey: nullIfEmpty(idempotencyKeyRef.current?.value ?? "")
            }
        })
    }

    return (
        <div className={cssClasses.kalenderavtale}>
            <SyntaxHighlighter language="graphql" style={darcula}>
                {print(NESTE_STEG_SAK)}
            </SyntaxHighlighter>
            <div style={{maxWidth: "35rem"}}>
                <TextField label={"Sak ID"} ref={sakIdRef}/>
                <TextField label={"Neste steg"} ref={nesteStegRef}/>
                <TextField label={"Idempotency key"} ref={idempotencyKeyRef}/>
                <Button style={{marginTop: "8px"}} onClick={handleSend}>Send</Button>
            </div>

            {loading && <p>Laster...</p>}
            {error &&
                <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data &&
                <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </div>
    )
}