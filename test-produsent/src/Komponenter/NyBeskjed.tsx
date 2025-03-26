import {gql, useMutation} from "@apollo/client";
import React, {useContext, useEffect} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {GrupperingsidContext} from "../App.tsx";
import cssClasses from "./KalenderAvtale.module.css";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import {Button, TextField} from "@navikt/ds-react";
import {EksternVarsel, formateEksternVarsel} from "./EksternVarsling.tsx";
import {MottakerInput, MottakerRef} from "./MottakerInput.tsx";

const NY_BESKJED = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $mottaker: MottakerInput!
        $lenke: String!
        $tekst: String!
        $eksternId: String!
        $merkelapp: String!
        $opprettetTidspunkt: ISO8601DateTime
        $eksterneVarsler: [EksterntVarselInput!]!
    ) {
        nyBeskjed(
            nyBeskjed: {
                mottakere: [$mottaker]
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
    }] = useMutation<Pick<Mutation, "nyBeskjed">>(NY_BESKJED);

    const grupperingsid = useContext(GrupperingsidContext);

    const grupperingsidRef = React.useRef<HTMLInputElement>(null);
    const virksomhetsnummerRef = React.useRef<HTMLInputElement>(null);
    const tekstRef = React.useRef<HTMLInputElement>(null);
    const merkelappRef = React.useRef<HTMLInputElement>(null);
    const lenkeRef = React.useRef<HTMLInputElement>(null);
    const eksternIdRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselRef = React.useRef<EksternVarsel>(null);
    const mottakerRef = React.useRef<MottakerRef>(null);
    const tidspunktIdRef = React.useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid;
        }
    }, [grupperingsid]);

    const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s


    const handleSend = () => {
        nyBeskjed({
            variables: {
                grupperingsid: nullIfEmpty(grupperingsidRef.current?.value),
                virksomhetsnummer: nullIfEmpty(virksomhetsnummerRef.current?.value),
                mottaker: mottakerRef.current?.hentMottaker(),
                tekst: nullIfEmpty(tekstRef.current?.value),
                merkelapp: nullIfEmpty(merkelappRef.current?.value),
                lenke: lenkeRef.current?.value ?? "",
                opprettetTidspunkt: nullIfEmpty(tidspunktIdRef.current?.value) ?? new Date().toISOString(),
                eksternId: nullIfEmpty(eksternIdRef.current?.value),
                eksterneVarsler: formateEksternVarsel(eksternVarselRef)
            }
        })
        if (eksternIdRef.current !== null) eksternIdRef.current.value = crypto.randomUUID().toString()
    }


    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}
                           lineProps={{style: {wordBreak: 'break-all', whiteSpace: 'pre-wrap'}}}
        >
            {print(NY_BESKJED)}
        </SyntaxHighlighter>
        <div style={{display: "grid", gridTemplateColumns: "1fr 1fr", width: "70rem", gap: "16px"}}>
            <div>
                <TextField label={"Grupperingsid*"} ref={grupperingsidRef}/>
                <TextField label={"Virksomhetsnummer*"} ref={virksomhetsnummerRef} defaultValue="211511052"/>
                <MottakerInput ref={mottakerRef}/>
                <TextField label={"Tekst*"} ref={tekstRef} defaultValue="Dette er en ny beskjed"/>
                <TextField label={"Merkelapp*"} ref={merkelappRef} defaultValue="fager"/>
                <TextField label={"Lenke*"} ref={lenkeRef}/>
                <TextField label={"EksternId*"} ref={eksternIdRef} defaultValue={crypto.randomUUID().toString()}/>
                <TextField label={`Opprettet (${new Date().toISOString()})`} ref={tidspunktIdRef} />
            </div>
            <EksternVarsel ref={eksternVarselRef}/>
        </div>

        <Button style={{maxWidth: "20rem"}} variant="primary"
                onClick={handleSend}>Opprett en ny beskjed</Button>

        {loading && <p>Laster...</p>}
        {error &&
            <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}
