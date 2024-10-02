import {gql, useMutation} from "@apollo/client";
import {Mutation} from "../api/graphql-types.ts";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import React, {useEffect} from "react";
import cssClasses from "./KalenderAvtale.module.css";
import {Button, TextField, ToggleGroup} from "@navikt/ds-react";
import {GrupperingsidContext} from "../App.tsx";


const TILLEGGSINFORMASJON_SAK = gql`
    mutation (
        $sakId: ID!
        $tilleggsinformasjon: String
        $idempotencyKey: String
    ) {
        tilleggsinformasjonSak(tilleggsinformasjon: $tilleggsinformasjon, id: $sakId, idempotencyKey: $idempotencyKey) {
            ... on TilleggsinformasjonSakVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const TILLEGGSINFORMASJON_SAK_GRUPPERINGSID = gql`
    mutation (
        $grupperingsid: String!
        $merkelapp: String!
        $tilleggsinformasjon: String
        $idempotencyKey: String
    ) {
        tilleggsinformasjonSakByGrupperingsid(grupperingsid: $grupperingsid, merkelapp: $merkelapp, tilleggsinformasjon: $tilleggsinformasjon, idempotencyKey: $idempotencyKey) {
            ... on TilleggsinformasjonSakVellykket {
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

export const TilleggsinformasjonSak = () => {
    const sakIdRef = React.useRef<HTMLInputElement>(null)
    const grupperingsidRef = React.useRef<HTMLInputElement>(null)
    const merkelappRef = React.useRef<HTMLInputElement>(null)
    const tilleggsinformasjonRef = React.useRef<HTMLInputElement>(null)
    const idempotencyKeyRef = React.useRef<HTMLInputElement>(null)
    const [queryType, setQueryType] = React.useState<QueryType>("Saksid")

    const grupperingsid = React.useContext(GrupperingsidContext)

    const [tilleggsinformasjonSak, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "tilleggsinformasjonSak" | "tilleggsinformasjonSakByGrupperingsid">>(queryType === "Saksid" ? TILLEGGSINFORMASJON_SAK : TILLEGGSINFORMASJON_SAK_GRUPPERINGSID)

    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid
        }
    }, [grupperingsid]);

    const handleSend = () => {
        tilleggsinformasjonSak({
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
                tilleggsinformasjon: nullIfEmpty(tilleggsinformasjonRef.current?.value ?? ""),
                idempotencyKey: nullIfEmpty(idempotencyKeyRef.current?.value ?? "")
            }
        })
    }

    return (
        <div className={cssClasses.kalenderavtale}>
            <SyntaxHighlighter language="graphql" style={darcula}>
                {print(TILLEGGSINFORMASJON_SAK)}
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
                <TextField label={"Tilleggsinformasjon"} ref={tilleggsinformasjonRef}/>
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