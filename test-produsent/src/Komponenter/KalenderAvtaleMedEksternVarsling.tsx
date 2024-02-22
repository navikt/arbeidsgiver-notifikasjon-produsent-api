import {gql, useMutation} from "@apollo/client";
import {print} from "graphql/language";
import React, {useState} from "react";
import {Mutation} from "../api/graphql-types.ts";
import {Button, Textarea} from "@navikt/ds-react";
import cssClasses from "./KalenderAvtaleMedEksternVarsling.module.css";
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { darcula } from 'react-syntax-highlighter/dist/esm/styles/prism';

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

export const KalenderAvtaleMedEksternVarsling: React.FunctionComponent = () => {
    const [nyKalenderavtale, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "nyKalenderavtale">>(NY_KALENDERAVTALE_MED_VARSSLING)


    const [variables, setVariables] = useState({
        grupperingsid: "8e3ffcd8-d547-4cfb-abc4-f923dd1c56de",
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
        }]
    });
    return <div className={cssClasses.kalenderavtale}>

        <SyntaxHighlighter language="graphql" style={darcula}>
            {print(NY_KALENDERAVTALE_MED_VARSSLING)}
        </SyntaxHighlighter>
        <Textarea
            style={{fontSize: "12px", lineHeight: "12px"}}
            label="Variabler"
            value={JSON.stringify(variables, null, 2)}
            onChange={(e) => setVariables(JSON.parse(e.target.value))}
        />
        <Button variant="primary"
                onClick={() => nyKalenderavtale({variables})}>Opprett kalenderavtale med ekstern varsling</Button>

        {loading && <p>Laster...</p>}
        {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
        {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}

    </div>
}