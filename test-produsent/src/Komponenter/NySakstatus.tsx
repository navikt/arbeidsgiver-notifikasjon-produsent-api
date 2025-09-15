import {gql} from "@apollo/client";
import {useMutation} from "@apollo/client/react";
import React, {useEffect, useState} from "react";
import {Mutation, SaksStatus} from "../api/graphql-types.ts";
import cssClasses from "./KalenderAvtale.module.css";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import {Button, TextField, ToggleGroup} from "@navikt/ds-react";
import {GrupperingsidContext} from "../App.tsx";


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

const NY_SAKSTATUS_GRUPPERINGSID = gql`
    mutation (
        $grupperingsid: String!
        $merkelapp: String!
        $nyStatus: SaksStatus!
        $nyLenkeTilSak: String
        $overstyrStatustekstMed: String
        $hardDelete: HardDeleteUpdateInput
        $tidspunkt: ISO8601DateTime
    ) {
        nyStatusSakByGrupperingsid(
            grupperingsid: $grupperingsid
            merkelapp: $merkelapp
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


type QueryType = "id" | "grupperingsid";

export const NySakstatus: React.FunctionComponent = () => {
    const idRef = React.useRef<HTMLInputElement>(null)
    const grupperingsidRef = React.useRef<HTMLInputElement>(null)
    const merkelappRef = React.useRef<HTMLInputElement>(null)
    const [nyStatus, setNyStatus] = useState<SaksStatus>("FERDIG" as SaksStatus)
    const nyLenkeTilSakRef = React.useRef<HTMLInputElement>(null)
    const overstyrStatustekstMedRef = React.useRef<HTMLInputElement>(null)
    const tidspunktRef = React.useRef<HTMLInputElement>(null)

    const grupperingsid = React.useContext(GrupperingsidContext)

    const [querytype, setQuerytype] = useState<QueryType>("id" as QueryType)

    const [nySakstatus, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nyStatusSak" | "nyStatusSakByGrupperingsid">>(querytype === "id" ? NY_SAKSTATUS : NY_SAKSTATUS_GRUPPERINGSID)

    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid
        }
    }, [grupperingsid]);

    const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s

    const handleSend = () => {
        nySakstatus({
            variables: {
                ...querytype === "id" ? {id: idRef.current?.value} : {
                    grupperingsid: grupperingsidRef.current?.value,
                    merkelapp: merkelappRef.current?.value
                },
                nyStatus: nyStatus,
                nyLenkeTilSak: nullIfEmpty(nyLenkeTilSakRef.current?.value),
                overstyrStatustekstMed: nullIfEmpty(overstyrStatustekstMedRef.current?.value),
                tidspunkt: nullIfEmpty(tidspunktRef.current?.value)
            }
        })
    }


    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_SAKSTATUS)}
        </SyntaxHighlighter>
        <div style={{maxWidth: "36rem", gap: "4px", display: "flex", flexDirection: "column"}}>
            <ToggleGroup onChange={(value) => setQuerytype(value as QueryType)}
                         defaultValue="id">
                <ToggleGroup.Item value={"id"}>Saksid</ToggleGroup.Item>
                <ToggleGroup.Item value={"grupperingsid"}>Grupperingsid/Merkelapp</ToggleGroup.Item>
            </ToggleGroup>
            {querytype === "id" ? <TextField label={"Id*"} ref={idRef}/>
                : <>
                    <TextField label={"Grupperingsid*"} ref={grupperingsidRef} defaultValue={grupperingsid}/>
                    <TextField label={"Merkelapp*"} ref={merkelappRef} defaultValue="fager"/>
                </>
            }
            <ToggleGroup defaultValue={nyStatus} onChange={(s) => setNyStatus(s as SaksStatus)} label="Ny status">
                {Object.values(SaksStatus).map((status) => <ToggleGroup.Item key={status}
                                                                             value={status}>{status}</ToggleGroup.Item>)}
            </ToggleGroup>
            <TextField label={"Ny lenke til sak"} ref={nyLenkeTilSakRef}/>
            <TextField label={"Overstyr tekst med"} ref={overstyrStatustekstMedRef}/>
            <TextField label={"Tidspunkt"} ref={tidspunktRef} defaultValue={"2025-12-03T10:15:30Z"}/>
            <Button variant="primary"
                    onClick={() => handleSend()}>Opprett en ny sakstatus</Button>
        </div>


        {loading && <p>Laster...</p>}
        {error &&
            <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}