import {gql, useMutation} from "@apollo/client";
import {print} from "graphql/language";
import React, {useContext, useEffect} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {Button, TextField, ToggleGroup} from '@navikt/ds-react';
import cssClasses from "./KalenderAvtale.module.css";
import {Prism as SyntaxHighlighter} from 'react-syntax-highlighter';
import {darcula} from 'react-syntax-highlighter/dist/esm/styles/prism';
import {GrupperingsidContext} from "../App.tsx";
import {EksternVarsel, formateEksternVarsel, formaterPåminnelse} from "./EksternVarsling.tsx";
import {MottakerInput, MottakerRef} from "./MottakerInput.tsx";

const NY_OPPGAVE = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $mottaker: MottakerInput!
        $lenke: String!
        $tekst: String!
        $merkelapp: String!
        $frist: ISO8601Date
        $eksternId: String!
        $opprettetTidspunkt: ISO8601DateTime
        $eksterneVarsler: [EksterntVarselInput!]!
        $paaminnelse: PaaminnelseInput
    ) {
        nyOppgave(
            nyOppgave: {
                mottakere: [$mottaker]
                frist: $frist
                notifikasjon: {
                    merkelapp: $merkelapp
                    lenke: $lenke
                    tekst: $tekst
                }
                metadata: {
                    grupperingsid: $grupperingsid
                    virksomhetsnummer: $virksomhetsnummer
                    eksternId: $eksternId
                    opprettetTidspunkt: $opprettetTidspunkt
                }
                eksterneVarsler: $eksterneVarsler
                paaminnelse: $paaminnelse
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

    const grupperingsidRef = React.useRef<HTMLInputElement>(null);
    const virksomhetsnummerRef = React.useRef<HTMLInputElement>(null);
    const tekstRef = React.useRef<HTMLInputElement>(null);
    const fristRef = React.useRef<HTMLInputElement>(null);
    const merkelappRef = React.useRef<HTMLInputElement>(null);
    const lenkeRef = React.useRef<HTMLInputElement>(null);
    const eksternIdRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselRef = React.useRef<EksternVarsel>(null);
    const mottakerRef = React.useRef<MottakerRef>(null);
    const tidspunktIdRef = React.useRef<HTMLInputElement>(null);

    const [harPaaminnelse, setHarPaaminnelse] = React.useState<boolean>(false);

    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid;
        }
    }, [grupperingsid]);

    const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s


    const handleSend = () => {
        nyOppgave({
            variables: {
                grupperingsid: nullIfEmpty(grupperingsidRef.current?.value),
                virksomhetsnummer: nullIfEmpty(virksomhetsnummerRef.current?.value),
                mottaker: mottakerRef.current?.hentMottaker(),
                lenke: lenkeRef.current?.value ?? "",
                tekst: nullIfEmpty(tekstRef.current?.value),
                eksternId: nullIfEmpty(eksternIdRef.current?.value),
                merkelapp: nullIfEmpty(merkelappRef.current?.value),
                opprettetTidspunkt: nullIfEmpty(tidspunktIdRef.current?.value) ?? new Date().toISOString(),
                paaminnelse: harPaaminnelse ? formaterPåminnelse(eksternVarselRef) : null,
                eksterneVarsler: formateEksternVarsel(eksternVarselRef)
            }
        })

        if (eksternIdRef.current !== null) eksternIdRef.current.value = crypto.randomUUID().toString()
    }

    return <div className={cssClasses.kalenderavtale}>
        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_OPPGAVE)}
        </SyntaxHighlighter>

        <div
            style={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr 1fr",
                width: "105rem",
                gap: "32px",
            }}
        >
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <TextField label={"Grupperingsid*"} ref={grupperingsidRef}/>
                <TextField label={"Virksomhetsnummer*"} ref={virksomhetsnummerRef} defaultValue="211511052"/>
                <MottakerInput ref={mottakerRef}/>
                <TextField label={"Tekst*"} ref={tekstRef} defaultValue="Dette er en oppgave"/>
                <TextField label={"Frist*"} ref={fristRef} defaultValue={"2024-05-17"}/>
                <TextField label={"Merkelapp*"} ref={merkelappRef} defaultValue="fager"/>
                <TextField label={"Lenke"} ref={lenkeRef}/>
                <TextField label={"EksternId*"} ref={eksternIdRef} defaultValue={crypto.randomUUID().toString()}/>
                <TextField label={`Opprettet (${new Date().toISOString()})`} ref={tidspunktIdRef} />
            </div>
            <div>
                <EksternVarsel ref={eksternVarselRef}/>
                <hr/>
                <ToggleGroup
                    defaultValue="Ingen"
                    onChange={(v) => setHarPaaminnelse(v === "Send påminnelse")}
                    label="Påminnelse"
                >
                    <ToggleGroup.Item value={"Ingen"}>Ingen</ToggleGroup.Item>
                    <ToggleGroup.Item value={"Send påminnelse"}>
                        Send påminnelse
                    </ToggleGroup.Item>
                </ToggleGroup>
            </div>
        </div>
        <Button style={{maxWidth: "20rem"}} variant="primary"
                onClick={handleSend}>Opprett en ny oppgave</Button>

        {loading && <p>Laster...</p>}
        {error &&
            <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data &&
            <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}