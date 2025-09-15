import {gql} from "@apollo/client";
import {useMutation} from "@apollo/client/react";
import {Mutation} from "../api/graphql-types.ts";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import React, {useEffect} from "react";
import cssClasses from "./KalenderAvtale.module.css";
import {Button, TextField, ToggleGroup} from "@navikt/ds-react";
import {GrupperingsidContext} from "../App.tsx";


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

const NESTE_STEG_SAK_GRUPPERINGSID = gql`
    mutation (
        $grupperingsid: String!
        $merkelapp: String!
        $nesteSteg: String
        $idempotencyKey: String
    ) {
        nesteStegSakByGrupperingsid(grupperingsid: $grupperingsid, merkelapp: $merkelapp, nesteSteg: $nesteSteg, idempotencyKey: $idempotencyKey) {
            ... on NesteStegSakVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s

type QueryType = "Saksid" | "Grupperingsid"

export const NesteStegSak = () => {
    const sakIdRef = React.useRef<HTMLInputElement>(null)
    const grupperingsidRef = React.useRef<HTMLInputElement>(null)
    const merkelappRef = React.useRef<HTMLInputElement>(null)
    const nesteStegRef = React.useRef<HTMLInputElement>(null)
    const idempotencyKeyRef = React.useRef<HTMLInputElement>(null)
    const [queryType, setQueryType] = React.useState<QueryType>("Saksid")

    const grupperingsid = React.useContext(GrupperingsidContext)

    const [nesteStegSak, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nesteStegSak" | "nesteStegSakByGrupperingsid">>(queryType === "Saksid" ? NESTE_STEG_SAK : NESTE_STEG_SAK_GRUPPERINGSID)

    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid
        }
    }, [grupperingsid]);

    const handleSend = () => {
        nesteStegSak({
            variables: {
                ...{
                    ...queryType == "Saksid" ? {
                        sakId: sakIdRef.current?.value
                    } : {
                        grupperingsid: grupperingsidRef.current?.value,
                        merkelapp: merkelappRef.current?.value
                    }
                },
                sakId: sakIdRef.current?.value ?? "",
                grupperingsid: nullIfEmpty(grupperingsidRef.current?.value ?? ""),
                merkelapp: nullIfEmpty(merkelappRef.current?.value ?? ""),
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
                <ToggleGroup onChange={(it) => setQueryType(it as QueryType)} defaultValue={queryType}>
                    <ToggleGroup.Item value="Saksid">Saksid</ToggleGroup.Item>
                    <ToggleGroup.Item value="Grupperingsid">Grupperingsid</ToggleGroup.Item>
                </ToggleGroup>
                {queryType === "Saksid" ?
                    <TextField label={"Sak ID"} ref={sakIdRef}/>
                    : <>
                        <TextField label={"Grupperingsid"} defaultValue={grupperingsid} ref={grupperingsidRef}/>
                        <TextField label={"Merkelapp"} defaultValue="fager" ref={merkelappRef}/>
                    </>
                }
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