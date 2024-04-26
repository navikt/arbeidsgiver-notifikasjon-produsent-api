import React from "react";
import {Textarea, TextField, ToggleGroup} from "@navikt/ds-react";

type EksternVarselValg = "Ingen" | "SMS" | "EPOST" | "Altinntjeneste"

export type EksternVarsel = SMS | Epost | Altinntjeneste | null


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
        eksternVarselAltinnInnholdRef = React.useRef<HTMLTextAreaElement>(null);

    React.useImperativeHandle(ref, () => {
        switch (eksternVarsel) {
            case "SMS":
                return {
                    tlf: eksternVarselSmsNrRef.current?.value,
                    smsTekst: eksternVarselSmsInnholdRef.current?.value ?? null,
                    tidspunkt: eksternVarselTidspunktRef.current?.value ?? null
                }
            case "EPOST":
                return {
                    epostadresse: eksternVarselEpostRef.current?.value ?? null,
                    epostTittel: eksternVarselEpostTittelRef.current?.value ?? null,
                    epostHtmlBody: eksternVarselEpostInnholdRef.current?.value ?? null,
                    tidspunkt: eksternVarselTidspunktRef.current?.value ?? null
                }
            case "Altinntjeneste":
                return {
                    serviceCode: eksternVarselAltinnServiceCodeRef.current?.value ?? null,
                    serviceEdition: eksternVarselAltinnServiceEditionRef.current?.value ?? null,
                    tittel: eksternVarselAltinnTittelRef.current?.value ?? null,
                    innhold: eksternVarselAltinnInnholdRef.current?.value ?? null,
                    tidspunkt: eksternVarselTidspunktRef.current?.value ?? null
                }
            default:
                return null
        }
    }, [eksternVarsel]);


    return <div>
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
});



export function formateEksternVarsel(eksternVarselRef: React.MutableRefObject<EksternVarsel>) {
    const varselfraref = eksternVarselRef.current
    if (varselfraref === null) return null
    else if ("tlf" in varselfraref) {
        return {
            sms: {
                mottaker: {
                    kontaktinfo: {
                        tlf: varselfraref.tlf
                    }
                },
                smsTekst: varselfraref.smsTekst,
                sendetidspunkt: {
                    tidspunkt: new Date().toISOString()
                }
            }
        }
    } else if ("epostadresse" in varselfraref) {
        return {
            epost: {
                mottaker: {
                    kontaktinfo: {
                        epostadresse: varselfraref.epostadresse
                    }},
                epostTittel: varselfraref.epostTittel,
                epostHtmlBody: varselfraref.epostHtmlBody,
                sendetidspunkt: {
                    tidspunkt: new Date().toISOString()
                }
            }
        }
    } else if ("serviceCode" in varselfraref) {
        return {
            altinntjeneste: {
                mottaker: {
                    serviceCode: varselfraref.serviceCode,
                    serviceEdition: varselfraref.serviceEdition
                },
                tittel: varselfraref.tittel,
                innhold: varselfraref.innhold,
                sendetidspunkt: {
                    tidspunkt: new Date().toISOString()
                }
            }
        }
    } else {
        return null;
    }
}