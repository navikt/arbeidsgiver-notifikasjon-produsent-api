import { gql } from '@apollo/client';
import { useMutation } from '@apollo/client/react';
import { Mutation, Sendevindu } from '../api/graphql-types.ts';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { darcula } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { print } from 'graphql/language';
import React from 'react';
import cssClasses from './KalenderAvtale.module.css';
import { Button, Checkbox, TextField, ToggleGroup } from '@navikt/ds-react';
import { Altinntjeneste, EksternVarsel, Epost, SMS } from './EksternVarsling.tsx';


const ENDRE_PAAMINNELSE_OPPGAVE = gql`
    mutation (
        $oppgaveId: ID!
        $paaminnelse: PaaminnelseInput
    ) {
        oppgaveEndrePaaminnelse(
            id: $oppgaveId
            paaminnelse: $paaminnelse 
        ) {
            ... on OppgaveEndrePaaminnelseVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`;

const ENDRE_PAAMINNELSE_OPPGAVE_EKSTERN_ID = gql`
    mutation (
        $eksternId: String!
        $merkelapp: String!
        $paaminnelse: PaaminnelseInput
    ) {
        oppgaveEndrePaaminnelseByEksternId(
            eksternId: $eksternId
            merkelapp: $merkelapp
            paaminnelse: $paaminnelse
        ) {
            ... on OppgaveEndrePaaminnelseVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`;

const nullIfEmpty = (s: string | undefined) => s === '' || s === undefined ? null : s;

type QueryType = 'Oppgave-id' | 'Ekstern id'


export const EndrePåminnelseOppgave = () => {
    const oppgaveIdRef = React.useRef<HTMLInputElement>(null);
    const eksternIdRef = React.useRef<HTMLInputElement>(null);
    const merkelappRef = React.useRef<HTMLInputElement>(null);
    const tidspunktRef = React.useRef<HTMLInputElement>(null);
    const eksternVarselRef = React.useRef<EksternVarsel>(null);

    const [queryType, setQueryType] = React.useState<QueryType>('Oppgave-id');
    const [paaminnelse, setPaaminnelse] = React.useState<boolean>(false);

    const [endrePaaminnelseOppgave, {
        data,
        loading,
        error,
    }] = useMutation<Pick<Mutation, 'oppgaveEndrePaaminnelse' | 'oppgaveEndrePaaminnelseByEksternId'>>(queryType === 'Oppgave-id' ? ENDRE_PAAMINNELSE_OPPGAVE : ENDRE_PAAMINNELSE_OPPGAVE_EKSTERN_ID);


    const handleSend = () => {
        endrePaaminnelseOppgave({
            variables: {
                ...{
                    ...queryType == 'Oppgave-id' ? {
                        oppgaveId: oppgaveIdRef.current?.value,
                    } : {
                        eksternId: eksternIdRef.current?.value,
                        merkelapp: merkelappRef.current?.value,
                    },
                },
                ...paaminnelse ? {
                    paaminnelse: {
                        tidspunkt:
                            { etterOpprettelse: nullIfEmpty(tidspunktRef.current?.value ?? '') },
                        eksterneVarsler: paaminnelse ? [handleEksternIdRef(eksternVarselRef)].filter(it => it !== null) : null,
                    },
                } : {
                    paaminnelse: null,
                },

            },
        });
    };

    return (
        <div className={cssClasses.kalenderavtale} style={{ minWidth: 'min(60rem, 100%)' }}>
            <SyntaxHighlighter language="graphql" style={darcula}>
                {queryType == 'Oppgave-id' ? print(ENDRE_PAAMINNELSE_OPPGAVE) : print(ENDRE_PAAMINNELSE_OPPGAVE_EKSTERN_ID)}
            </SyntaxHighlighter>
            <div style={{ maxWidth: '35rem' }}>
                <ToggleGroup onChange={(it) => setQueryType(it as QueryType)} defaultValue={'Oppgave-id'}>
                    <ToggleGroup.Item value="Oppgave-id">Oppgave-id</ToggleGroup.Item>
                    <ToggleGroup.Item value="Ekstern id">Ekstern id</ToggleGroup.Item>
                </ToggleGroup>
                {queryType === 'Oppgave-id' ?
                    <TextField label="Oppgave-Id" ref={oppgaveIdRef} />
                    : <>
                        <TextField label="Ekstern Id" defaultValue={''} ref={eksternIdRef} />
                        <TextField label="Merkelapp" defaultValue={''} ref={merkelappRef} />

                    </>
                }
                <Checkbox onChange={() => setPaaminnelse(!paaminnelse)} checked={paaminnelse}>
                    Skal ha påminnelse
                </Checkbox>
                {paaminnelse && <>
                    <TextField label="Tidspunkt (ISO8601Duration fra endring)" ref={tidspunktRef} />
                    <EksternVarsel ref={eksternVarselRef} />
                </>}
                <Button style={{ marginTop: '8px' }} onClick={handleSend}>Send</Button>
            </div>

            {loading && <p>Laster...</p>}
            {error &&
                <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data &&
                <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </div>
    );
};


const handleEksternIdRef = (paaminnelseRef: React.MutableRefObject<EksternVarsel | null>) => {
    const varselfraref = paaminnelseRef?.current?.hentEksternVarsel();
    if (varselfraref === null || varselfraref === undefined) return null;
    else if ('tlf' in varselfraref) {
        const { tlf, smsTekst, tidspunkt } = varselfraref as SMS;
        if (nullIfEmpty(tlf) === null ||
            nullIfEmpty(smsTekst) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return [];
        return {
            sms: {
                mottaker: {
                    kontaktinfo: {
                        tlf: tlf,
                    },
                },
                smsTekst: smsTekst,
                sendevindu: Sendevindu.Loepende,
            },
        };
    } else if ('epostadresse' in varselfraref) {
        const { epostadresse, epostTittel, epostHtmlBody, tidspunkt } = varselfraref as Epost;
        if (nullIfEmpty(epostadresse) === null ||
            nullIfEmpty(epostTittel) === null ||
            nullIfEmpty(epostHtmlBody) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null;
        return {

            epost: {
                mottaker: {
                    kontaktinfo: {
                        epostadresse: epostadresse,
                    },
                },
                epostTittel: epostTittel,
                epostHtmlBody: epostHtmlBody,
                sendevindu: Sendevindu.Loepende,
            },
        };
    } else if ('serviceCode' in varselfraref) {
        const { serviceCode, serviceEdition, tittel, innhold, tidspunkt } = varselfraref as Altinntjeneste;
        if (nullIfEmpty(serviceCode) === null ||
            nullIfEmpty(serviceEdition) === null ||
            nullIfEmpty(tittel) === null ||
            nullIfEmpty(innhold) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null;
        return {
            altinntjeneste: {
                mottaker: {
                    serviceCode: serviceCode,
                    serviceEdition: serviceEdition,
                },
                tittel: tittel,
                innhold: innhold,
                sendevindu: Sendevindu.Loepende,
            },
        };
    }
    return null;
};