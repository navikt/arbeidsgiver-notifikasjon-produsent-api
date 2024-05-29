import {gql, useMutation} from "@apollo/client";
import {print} from "graphql/language";
import React, {useEffect} from "react";
import {Mutation, SaksStatus} from "../api/graphql-types.ts";
import {Button, TextField} from "@navikt/ds-react";
import cssClasses from "./KalenderAvtale.module.css";
import {Prism as SyntaxHighlighter} from 'react-syntax-highlighter';
import {darcula} from 'react-syntax-highlighter/dist/esm/styles/prism';
import {GrupperingsidContext} from "../App.tsx";

const NY_SAK = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $lenke: String
        $tittel: String!
        $merkelapp: String!
        $initiellStatus: SaksStatus!
        $nesteSteg: String
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
            merkelapp: $merkelapp,
            initiellStatus: $initiellStatus
            nesteSteg: $nesteSteg
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
    const grupperingsid = React.useContext(GrupperingsidContext)

    const grupperingsidRef = React.useRef<HTMLInputElement>(null)
    const virksomhetsnummerRef = React.useRef<HTMLInputElement>(null)
    const eksternIdRef = React.useRef<HTMLInputElement>(null)
    const lenkeRef = React.useRef<HTMLInputElement>(null)
    const tittelRef = React.useRef<HTMLInputElement>(null)
    const merkelapp = React.useRef<HTMLInputElement>(null)
    const initiellStatusRef = React.useRef<HTMLInputElement>(null)
    const nesteStegRef = React.useRef<HTMLInputElement>(null)

    const [nySak, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nySak">>(NY_SAK)


    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid
        }
    }, [grupperingsid]);

    const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s


    const handleSend = () => {
        nySak({
            variables: {
                grupperingsid: nullIfEmpty(grupperingsidRef.current?.value),
                virksomhetsnummer: nullIfEmpty(virksomhetsnummerRef.current?.value),
                eksternId: nullIfEmpty(eksternIdRef.current?.value),
                lenke: nullIfEmpty(lenkeRef.current?.value),
                tittel: nullIfEmpty(tittelRef.current?.value),
                initiellStatus: initiellStatusRef.current?.value as SaksStatus,
                nesteSteg: nullIfEmpty(nesteStegRef.current?.value),
                merkelapp: nullIfEmpty(merkelapp.current?.value)
            }
        })
        if (eksternIdRef.current !== null) eksternIdRef.current.value = crypto.randomUUID().toString()
    }

    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_SAK)}
        </SyntaxHighlighter>
        <div style={{maxWidth:"35rem"}}>
            <TextField label={"Grupperingsid*"}  ref={grupperingsidRef}/>
            <TextField label={"Virksomhetsnummer*"} ref={virksomhetsnummerRef} defaultValue="910825526"/>
            <TextField label={"EksternId*"} ref={eksternIdRef} defaultValue={crypto.randomUUID().toString()}/>
            <TextField label={"Lenke"} ref={lenkeRef} defaultValue={"https://foo.bar"}/>
            <TextField label={"Tittel*"} ref={tittelRef} defaultValue="Dette er en ny sak"/>
            <TextField label={"Merkelapp*"} ref={merkelapp} defaultValue="fager"/>
            <TextField label={"Initiell status*"} ref={initiellStatusRef} defaultValue="MOTTATT"/>
            <TextField label={"Neste steg"} ref={nesteStegRef} defaultValue="Saken er ventet ferdig behandlet Januar 2050" />

        </div>
        <Button variant="primary"
                onClick={handleSend}>Opprett en ny sak</Button>

        {loading && <p>Laster...</p>}
        {error &&
            <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}