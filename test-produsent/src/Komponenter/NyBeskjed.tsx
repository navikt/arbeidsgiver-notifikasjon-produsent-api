import {gql, useMutation} from "@apollo/client";
import React, {useContext, useEffect} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {GrupperingsidContext} from "../App.tsx";
import cssClasses from "./KalenderAvtaleMedEksternVarsling.module.css";
import {Prism as SyntaxHighlighter} from "react-syntax-highlighter";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import {Button, Textarea, TextField, ToggleGroup} from "@navikt/ds-react";

const NY_BESKJED = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $lenke: String!
        $tekst: String!
        $eksternId: String!
        $merkelapp: String!
        $opprettetTidspunkt: ISO8601DateTime

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
                    opprettetTidspunkt: $opprettetTidspunkt
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

type EksternVarselValg = "Ingen" | "SMS" | "EPOST" | "Altinntjeneste"

export const NyBeskjed: React.FunctionComponent = () => {
    const [nyBeskjed, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nyBeskjed">>(NY_BESKJED)

    const grupperingsid = useContext(GrupperingsidContext)
    const [eksternVarsel, setEksternVarsel] = React.useState<EksternVarselValg>("Ingen")


    const grupperingsidRef = React.useRef<HTMLInputElement>(null);
    const merkelappRef = React.useRef<HTMLInputElement>(null);
    const virksomhetsnummerRef = React.useRef<HTMLInputElement>(null);
    const lenkeRef = React.useRef<HTMLInputElement>(null);
    const tekstRef = React.useRef<HTMLInputElement>(null);

    const eksternIdRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselSmsNrRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselSmsInnholdRef = React.useRef<HTMLTextAreaElement>(null);
    const eksternVarselEpostRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselEpostTittelRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselEpostInnholdRef = React.useRef<HTMLTextAreaElement>(null);
    const eksternVarselTidspunktRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselAltinnServiceCodeRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselAltinnServiceEditionRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselAltinnTittelRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselAltinnInnholdRef = React.useRef<HTMLTextAreaElement>(null);


    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid;
        }
    }, [grupperingsid]);

    const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s


    const handleSend = () => {
        const eksterneVarsler = eksternVarsel === "Ingen" ? [] : [{
            sms: eksternVarsel === "SMS" ? {
                mottaker: {
                    kontaktinfo: {
                        tlf: nullIfEmpty(eksternVarselSmsNrRef.current?.value)
                    },
                },
                smsTekst: nullIfEmpty(eksternVarselSmsInnholdRef.current?.value),
                sendetidspunkt: {
                    tidspunkt: nullIfEmpty(eksternVarselTidspunktRef.current?.value)
                },
            } : null,
            epost: eksternVarsel === "EPOST" ? {
                mottaker: {
                    kontaktinfo: {
                        epostadresse: nullIfEmpty(eksternVarselEpostRef.current?.value)
                    }
                },
                epostTittel: nullIfEmpty(eksternVarselEpostTittelRef.current?.value),
                epostHtmlBody: nullIfEmpty(eksternVarselEpostInnholdRef.current?.value),
                sendetidspunkt: {
                    tidspunkt: nullIfEmpty(eksternVarselTidspunktRef.current?.value)
                }
            } : null,
            altinntjeneste: eksternVarsel === "Altinntjeneste" ? {
                mottaker: {
                    serviceCode: nullIfEmpty(eksternVarselAltinnServiceCodeRef.current?.value),
                    serviceEdition: nullIfEmpty(eksternVarselAltinnServiceEditionRef.current?.value)
                },
                tittel: nullIfEmpty(eksternVarselAltinnTittelRef.current?.value),
                innhold: nullIfEmpty(eksternVarselAltinnInnholdRef.current?.value),
                sendetidspunkt: {
                    tidspunkt: nullIfEmpty(eksternVarselTidspunktRef.current?.value)
                },
            } : null
        }]
        nyBeskjed({
            variables: {
                grupperingsid: nullIfEmpty(grupperingsidRef.current?.value),
                virksomhetsnummer: nullIfEmpty(virksomhetsnummerRef.current?.value),
                lenke: lenkeRef.current?.value ?? "",
                tekst: nullIfEmpty(tekstRef.current?.value),
                eksternId: nullIfEmpty(eksternIdRef.current?.value),
                merkelapp: nullIfEmpty(merkelappRef.current?.value),
                opprettetTidspunkt: new Date().toISOString(),
                eksterneVarsler: eksterneVarsler
            }
        })
    }


    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}
                           lineProps={{style: {wordBreak: 'break-all', whiteSpace: 'pre-wrap'}}}
        >
            {print(NY_BESKJED)}
        </SyntaxHighlighter>
        <div style={{display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px"}}>
            <div>
                <TextField label={"Grupperingsid*"} ref={grupperingsidRef}/>
                <TextField label={"Merkelapp*"} ref={merkelappRef} defaultValue="fager"/>
                <TextField label={"Virksomhetsnummer*"} ref={virksomhetsnummerRef} defaultValue="910825526"/>
                <TextField label={"Lenke*"} ref={lenkeRef}/>
                <TextField label={"Tekst*"} ref={tekstRef} defaultValue="Dette er en ny beskjed"/>
                <TextField label={"EksternId*"} ref={eksternIdRef} defaultValue={crypto.randomUUID().toString()}/>
            </div>
            <div>
                <ToggleGroup defaultValue={eksternVarsel}
                             onChange={(value) => setEksternVarsel(value as EksternVarselValg)}
                             label="Send ekstern varsel">
                    <ToggleGroup.Item value="Ingen">Ingen</ToggleGroup.Item>
                    <ToggleGroup.Item value="SMS">SMS</ToggleGroup.Item>
                    <ToggleGroup.Item value="EPOST">Epost</ToggleGroup.Item>
                    <ToggleGroup.Item value="Altinntjeneste">Altinntjeneste</ToggleGroup.Item>
                </ToggleGroup>
                {eksternVarsel === "SMS" ?
                    <>
                        <TextField label={"Sms nr"} ref={eksternVarselSmsNrRef} defaultValue={"99999999"}/>
                        <Textarea label={"Sms innhold"} ref={eksternVarselSmsInnholdRef}
                                  defaultValue={"Dette er en sms-varsel. \nLogg inn på NAV.no/min-side-arbeidsgiver for å lese mer."}/>
                    </> : null}
                {eksternVarsel === "EPOST" ?
                    <>
                        <TextField label={"Epost*"} ref={eksternVarselEpostRef} defaultValue="foo@bar.baz"/>
                        <TextField label={"Epost tittel*"} ref={eksternVarselEpostTittelRef}/>
                        <Textarea label={"Epost innhold"} ref={eksternVarselEpostInnholdRef}/>
                    </> : null}
                {eksternVarsel === "Altinntjeneste" ? <>
                    <TextField label={"Service code"} ref={eksternVarselAltinnServiceCodeRef}/>
                    <TextField label={"Service edition"} ref={eksternVarselAltinnServiceEditionRef}/>
                    <TextField label="Tittel" ref={eksternVarselAltinnTittelRef}/>
                    <Textarea label="Innhold" ref={eksternVarselAltinnInnholdRef}/>
                </> : null}
                {eksternVarsel !== "Ingen" ?
                    <TextField label={"Tidspunkt: \"YYYY-MM-DDThh:mm\""} ref={eksternVarselTidspunktRef}
                               defaultValue={"2024-12-19T13:30"}/> : null}
            </div>
        </div>

        <Button style={{maxWidth: "20rem"}} variant="primary"
                onClick={handleSend}>Opprett en ny beskjed</Button>

        {loading && <p>Laster...</p>}
        {error &&
            <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
    </div>
}
