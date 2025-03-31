import React from "react";
import {Textarea, TextField, ToggleGroup} from "@navikt/ds-react";
import {Sendevindu} from "../api/graphql-types.ts";

type EksternVarselValg = "Ingen" | "SMS" | "EPOST" | "Altinntjeneste" | "Altinnressurs"

export type EksternVarsel = { hentEksternVarsel: () => SMS | Epost | Altinntjeneste | Altinnressurs | null }


export type SMS = {
    tlf: string,
    smsTekst: string,
    tidspunkt: string
}

export type Epost = {
    epostadresse: string,
    epostTittel: string,
    epostHtmlBody: string,
    tidspunkt: string
}

export type Altinntjeneste = {
    serviceCode: string,
    serviceEdition: string,
    tittel: string,
    innhold: string,
    tidspunkt: string
}

export type Altinnressurs = {
    ressursId: string,
    epostTittel: string,
    epostHtmlBody: string,
    smsTekst: string,
    tidspunkt: string
}

const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s

export const EksternVarsel = React.forwardRef((_props, ref) => {
    const [eksternVarsel, setEksternVarsel] = React.useState<EksternVarselValg>("Ingen");
    const
        eksternVarselSmsNrRef = React.useRef<HTMLInputElement>(null),
        eksternVarselSmsInnholdRef = React.useRef<HTMLTextAreaElement>(null),
        eksternVarselEpostRef = React.useRef<HTMLInputElement>(null),
        eksternVarselEpostTittelRef = React.useRef<HTMLInputElement>(null),
        eksternVarselEpostInnholdRef = React.useRef<HTMLTextAreaElement>(null),
        eksternVarselTidspunktRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnServiceCodeRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnServiceEditionRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnTittelRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnInnholdRef = React.useRef<HTMLTextAreaElement>(null),
        eksternVarselAltinnRessursIdRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnRessursEpostTittelRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnRessursEpostHtmlBodyRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnRessursSmsTekstRef = React.useRef<HTMLInputElement>(null);

    React.useImperativeHandle(ref, () => ({
        hentEksternVarsel: () => {
            switch (eksternVarsel) {
                case "SMS":
                    return {
                        tlf: eksternVarselSmsNrRef.current?.value,
                        smsTekst: eksternVarselSmsInnholdRef.current?.value,
                        tidspunkt: eksternVarselTidspunktRef.current?.value
                    }
                case "EPOST":
                    return {
                        epostadresse: eksternVarselEpostRef.current?.value,
                        epostTittel: eksternVarselEpostTittelRef.current?.value,
                        epostHtmlBody: eksternVarselEpostInnholdRef.current?.value,
                        tidspunkt: eksternVarselTidspunktRef.current?.value
                    }
                case "Altinntjeneste":
                    return {
                        serviceCode: eksternVarselAltinnServiceCodeRef.current?.value,
                        serviceEdition: eksternVarselAltinnServiceEditionRef.current?.value,
                        tittel: eksternVarselAltinnTittelRef.current?.value,
                        innhold: eksternVarselAltinnInnholdRef.current?.value,
                        tidspunkt: eksternVarselTidspunktRef.current?.value
                    }
                case "Altinnressurs":
                    return {
                        ressursId: eksternVarselAltinnRessursIdRef.current?.value,
                        epostTittel: eksternVarselAltinnRessursEpostTittelRef.current?.value,
                        epostHtmlBody: eksternVarselAltinnRessursEpostHtmlBodyRef.current?.value,
                        smsTekst: eksternVarselAltinnRessursSmsTekstRef.current?.value,
                        tidspunkt: eksternVarselTidspunktRef.current?.value
                    }
                default:
                    return null
            }
        }
    }));


    return <div>
        <ToggleGroup defaultValue={eksternVarsel}
                     onChange={(value) => setEksternVarsel(value as EksternVarselValg)}
                     label="Send ekstern varsel">
            <ToggleGroup.Item value="Ingen">Ingen</ToggleGroup.Item>
            <ToggleGroup.Item value="SMS">SMS</ToggleGroup.Item>
            <ToggleGroup.Item value="EPOST">Epost</ToggleGroup.Item>
            <ToggleGroup.Item value="Altinntjeneste">Altinntjeneste</ToggleGroup.Item>
            <ToggleGroup.Item value="Altinnressurs">Altinnressurs</ToggleGroup.Item>
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
        {eksternVarsel === "Altinnressurs" ? <>
            <TextField label={"RessursId"} ref={eksternVarselAltinnRessursIdRef}/>
            <TextField label="Epost tittel" ref={eksternVarselAltinnRessursEpostTittelRef}/>
            <TextField label="Epost body" ref={eksternVarselAltinnRessursEpostHtmlBodyRef}/>
            <TextField label="Sms tekst" ref={eksternVarselAltinnRessursSmsTekstRef}/>
        </> : null}
        {eksternVarsel !== "Ingen" ?
            <TextField label={"Tidspunkt: \"YYYY-MM-DDThh:mm\""} ref={eksternVarselTidspunktRef}
                       defaultValue={new Date().toISOString().replace(/\..+/, '')}/> : null}
    </div>
});

export function formateEksternVarsel(eksternVarselRef: React.MutableRefObject<EksternVarsel | null>) {
    const varselfraref = eksternVarselRef?.current?.hentEksternVarsel()
    if (varselfraref === null || varselfraref === undefined) return []
    else if ("tlf" in varselfraref) {
        const {tlf, smsTekst, tidspunkt} = varselfraref as SMS
        if (nullIfEmpty(tlf) === null ||
            nullIfEmpty(smsTekst) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return []
        return {
            sms: {
                mottaker: {
                    kontaktinfo: {
                        tlf: tlf
                    }
                },
                smsTekst: smsTekst,
                sendetidspunkt: {
                    tidspunkt: tidspunkt
                }
            }
        }
    } else if ("epostadresse" in varselfraref) {
        const {epostadresse, epostTittel, epostHtmlBody, tidspunkt} = varselfraref as Epost
        if (nullIfEmpty(epostadresse) === null ||
            nullIfEmpty(epostTittel) === null ||
            nullIfEmpty(epostHtmlBody) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null
        return {
            epost: {
                mottaker: {
                    kontaktinfo: {
                        epostadresse: epostadresse
                    }
                },
                epostTittel: epostTittel,
                epostHtmlBody: epostHtmlBody,
                sendetidspunkt: {
                    tidspunkt: tidspunkt
                }
            }
        }
    } else if ("serviceCode" in varselfraref) {
        const {serviceCode, serviceEdition, tittel, innhold, tidspunkt} = varselfraref as Altinntjeneste
        if (nullIfEmpty(serviceCode) === null ||
            nullIfEmpty(serviceEdition) === null ||
            nullIfEmpty(tittel) === null ||
            nullIfEmpty(innhold) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null
        return {
            altinntjeneste: {
                mottaker: {
                    serviceCode: serviceCode,
                    serviceEdition: serviceEdition
                },
                tittel: tittel,
                innhold: innhold,
                sendetidspunkt: {
                    tidspunkt: tidspunkt
                }
            }
        }
    } else if ("ressursId" in varselfraref) {
        const {ressursId, epostTittel, epostHtmlBody, smsTekst, tidspunkt} = varselfraref as Altinnressurs
        if (nullIfEmpty(ressursId) === null ||
            nullIfEmpty(epostTittel) === null ||
            nullIfEmpty(epostHtmlBody) === null ||
            nullIfEmpty(smsTekst) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null
        return {
            altinnressurs: {
                mottaker: {
                    ressursId: ressursId
                },
                epostTittel: epostTittel,
                epostHtmlBody: epostHtmlBody,
                smsTekst: smsTekst,
                sendetidspunkt: {
                    tidspunkt: tidspunkt
                }
            }
        }
    } else {
        return [];
    }
}

export const formaterPåminnelse = (påminnelseRef: React.MutableRefObject<EksternVarsel | null>) => {
    const varselfraref = påminnelseRef?.current?.hentEksternVarsel()
    if (varselfraref === null || varselfraref === undefined) return null
    else if ("tlf" in varselfraref) {
        const {tlf, smsTekst, tidspunkt} = varselfraref as SMS
        if (nullIfEmpty(tlf) === null ||
            nullIfEmpty(smsTekst) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return []
        return {
            tidspunkt: {
                etterOpprettelse: "PT1M"
            },
            eksterneVarsler: [{
                sms: {
                    mottaker: {
                        kontaktinfo: {
                            tlf: tlf
                        }
                    },
                    smsTekst: smsTekst,
                    sendevindu: Sendevindu.NksAapningstid
                }
            }]
        }
    } else if ("epostadresse" in varselfraref) {
        const {epostadresse, epostTittel, epostHtmlBody, tidspunkt} = varselfraref as Epost
        if (nullIfEmpty(epostadresse) === null ||
            nullIfEmpty(epostTittel) === null ||
            nullIfEmpty(epostHtmlBody) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null
        return {
            tidspunkt: {
                etterOpprettelse: "PT1M"
            },
            eksterneVarsler: [{
                epost: {
                    mottaker: {
                        kontaktinfo: {
                            epostadresse: epostadresse
                        }
                    },
                    epostTittel: epostTittel,
                    epostHtmlBody: epostHtmlBody,
                    sendevindu: Sendevindu.NksAapningstid

                }
            }]
        }
    } else if ("serviceCode" in varselfraref) {
        const {serviceCode, serviceEdition, tittel, innhold, tidspunkt} = varselfraref as Altinntjeneste
        if (nullIfEmpty(serviceCode) === null ||
            nullIfEmpty(serviceEdition) === null ||
            nullIfEmpty(tittel) === null ||
            nullIfEmpty(innhold) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null
        return {
            tidspunkt: {
                etterOpprettelse: "PT1M"
            },
            eksterneVarsler: [{
                altinntjeneste: {
                    mottaker: {
                        serviceCode: serviceCode,
                        serviceEdition: serviceEdition,
                    },
                    tittel: tittel,
                    innhold: innhold,
                    sendevindu: Sendevindu.NksAapningstid
                }
            }]
        }
    } else if ("ressursId" in varselfraref) {
        const {ressursId, epostTittel, epostHtmlBody, smsTekst, tidspunkt} = varselfraref as Altinnressurs
        if (nullIfEmpty(ressursId) === null ||
            nullIfEmpty(epostTittel) === null ||
            nullIfEmpty(epostHtmlBody) === null ||
            nullIfEmpty(smsTekst) === null ||
            nullIfEmpty(tidspunkt) === null
        ) return null
        return {
            tidspunkt: {
                etterOpprettelse: "PT1M"
            },
            eksterneVarsler: [{
                altinnressurs: {
                    mottaker: {
                        ressursId: ressursId
                    },
                    epostTittel: epostTittel,
                    epostHtmlBody: epostHtmlBody,
                    smsTekst: smsTekst,
                    sendevindu: Sendevindu.NksAapningstid
                }
            }]

        }
    } else {
        return null
    }
}