import {gql, useMutation} from "@apollo/client";
import {print} from "graphql/language";
import {useContext, useState, FunctionComponent, useEffect, useRef} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {Button, Textarea} from "@navikt/ds-react";
import cssClasses from "./KalenderAvtaleMedEksternVarsling.module.css";
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { darcula } from 'react-syntax-highlighter/dist/esm/styles/prism';
import {GrupperingsidContext} from "../App.tsx";

const NY_KALENDERAVTALE_MED_VARSSLING = gql`
    mutation (
        $grupperingsid: String!
        $virksomhetsnummer: String!
        $eksternId: String!
        $lenke: String!
        $tekst: String!
        $startTidspunkt: ISO8601LocalDateTime!
        $sluttTidspunkt: ISO8601LocalDateTime
        $eksterneVarsler: [EksterntVarselInput!]!
        $paaminnelse: PaaminnelseInput
        $lokasjon: LokasjonInput
    ) {
        nyKalenderavtale(
            mottakere: [{
                altinn: {
                    serviceCode: "4936"
                    serviceEdition: "1"
                }
            }]
            virksomhetsnummer: $virksomhetsnummer,
            grupperingsid: $grupperingsid
            eksternId: $eksternId
            startTidspunkt: $startTidspunkt
            sluttTidspunkt: $sluttTidspunkt
            lenke: $lenke
            tekst: $tekst
            merkelapp: "fager"
            lokasjon: $lokasjon
            erDigitalt: true
            eksterneVarsler: $eksterneVarsler
            paaminnelse: $paaminnelse
        ) {
            __typename
            ... on NyKalenderavtaleVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const datePlus = (days: number = 0, hours: number = 0) => {
    const date = new Date();
    date.setDate(date.getDate() + days)
    date.setHours(date.getHours() + hours)
    return date
}

export const NyKalenderAvtaleMedEksternVarsling: FunctionComponent = () => {
    const [nyKalenderavtale, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nyKalenderavtale">>(NY_KALENDERAVTALE_MED_VARSSLING)

    const grupperingsid = useContext(GrupperingsidContext)
    const [variables, setVariables] = useState({
        grupperingsid: grupperingsid,
        virksomhetsnummer: "910825526",
        eksternId: "123",
        lenke: "https://foo.bar",
        tekst: "Dette er en kalenderavtale",
        startTidspunkt: datePlus(1).toISOString().replace('Z', ''),
        sluttTidspunkt: datePlus(1, 1).toISOString().replace('Z', ''),
        lokasjon: {
            postnummer: "1234",
            poststed: "Kneika",
            adresse: "rundt svingen og borti høgget"
        },
        eksterneVarsler: [{
            epost: {
                mottaker: {
                    kontaktinfo: {
                        epostadresse: "donald@duck.co"
                    }
                },
                epostTittel: "Varsel fra testpodusent",
                epostHtmlBody: "<h1>Hei</h1><p>Dette er en test</p>",
                sendetidspunkt: {
                    sendevindu: "LOEPENDE",
                }
            }
        }],
        paaminnelse: {
            tidspunkt: {
                foerStartTidspunkt: "PT1H"
            },
            eksterneVarsler: {
                epost: {
                    mottaker: {
                        kontaktinfo: {
                            epostadresse: "donald@duck.co"
                        }
                    },
                    epostTittel: "Varsel ved påminnelse fra testpodusent",
                    epostHtmlBody: "<h1>Hei</h1><p>Dette er en påminnelse</p>",
                    sendevindu: "LOEPENDE",
                }
            }
        }
    });

    useEffect(() => {
        setVariables({
            ...variables,
            grupperingsid: grupperingsid,
        })
    }, [grupperingsid]);

    const varsRef = useRef<HTMLTextAreaElement>(null)
    const [varsError, setVarsError] = useState<any | null>(null)
    useEffect(() => {
        if (varsRef.current) {
            varsRef.current.value = JSON.stringify(variables, null, 2)
        }
    }, []);


    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_KALENDERAVTALE_MED_VARSSLING)}
        </SyntaxHighlighter>
        <Textarea
            error={varsError}
            ref={varsRef}
            style={{fontSize: "12px", lineHeight: "12px"}}
            label="Variabler"
            onChange={(e) => {
                try {
                    setVariables(JSON.parse(e.target.value));
                    setVarsError(null)
                } catch (e: Error | any) {
                    setVarsError(e?.message ?? JSON.stringify(e, null, 2))
                }
            }}
        />
        { varsError !== null && <Button variant="danger" onClick={() => {
            if (varsRef.current !== null) {
                varsRef.current.value = JSON.stringify(variables, null, 2)
                setVarsError(null)
            }
        }}>nullstill variabler</Button> }
        <Button variant="primary"
                onClick={() => nyKalenderavtale({variables})}>Opprett kalenderavtale med ekstern varsling</Button>

        {loading && <p>Laster...</p>}
        {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}

    </div>
}

const OPPDATER_KALENDERAVTALE_MED_VARSLING = gql`
    mutation (
        $id: ID!
        $lenke: String
        $tekst: String
        $idempotenceKey: String
        $eksterneVarsler: [EksterntVarselInput!]! = []
        $lokasjon: LokasjonInput
    ) {
        oppdaterKalenderavtale(
            id: $id
            idempotencyKey: $idempotenceKey
            nyLenke: $lenke
            nyTekst: $tekst
            nyLokasjon: $lokasjon
            eksterneVarsler: $eksterneVarsler
        ) {
            __typename
            ... on OppdaterKalenderavtaleVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

export const OppdaterKalenderAvtaleMedEksternVarsling: FunctionComponent = () => {
    const [oppdaterKalenderavtale, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "oppdaterKalenderavtale">>(OPPDATER_KALENDERAVTALE_MED_VARSLING)


    const [variables, setVariables] = useState({
        id: "42",
        lenke: "https://foo.bar",
        tekst: "Dette er en kalenderavtale",
        lokasjon: {
            postnummer: "1234",
            poststed: "Kneika",
            adresse: "rundt svingen og borti høgget"
        },
        eksterneVarsler: [{
            epost: {
                mottaker: {
                    kontaktinfo: {
                        epostadresse: "donald@duck.co"
                    }
                },
                epostTittel: "Varsel fra testpodusent",
                epostHtmlBody: "<h1>Hei</h1><p>Dette er en test</p>",
                sendevindu: "LOEPENDE",
            }
        }]
    });
    const varsRef = useRef<HTMLTextAreaElement>(null)
    const [varsError, setVarsError] = useState<any | null>(null)
    useEffect(() => {
        if (varsRef.current) {
            varsRef.current.value = JSON.stringify(variables, null, 2)
        }
    }, []);

    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(OPPDATER_KALENDERAVTALE_MED_VARSLING)}
        </SyntaxHighlighter>
        <Textarea
            error={varsError}
            ref={varsRef}
            style={{fontSize: "12px", lineHeight: "12px"}}
            label="Variabler"
            onChange={(e) => {
                try {
                    setVariables(JSON.parse(e.target.value));
                    setVarsError(null)
                } catch (e: Error | any) {
                    setVarsError(e?.message ?? JSON.stringify(e, null, 2))
                }
            }}
        />
        { varsError !== null && <Button variant="danger" onClick={() => {
            if (varsRef.current !== null) {
                varsRef.current.value = JSON.stringify(variables, null, 2)
                setVarsError(null)
            }
        }}>nullstill variabler</Button> }
        <Button variant="primary"
                onClick={() => oppdaterKalenderavtale({variables})}>Oppdater kalenderavtale med ekstern varsling</Button>

        {loading && <p>Laster...</p>}
        {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}

    </div>
}